package com.evolt.teamecon.network;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.client.ClientShopCache;
import com.evolt.teamecon.network.payloads.ShopActionPayload;
import com.evolt.teamecon.network.payloads.ShopSyncPayload;
import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = TeamEconomyMod.MOD_ID)
public final class ShopNetwork {
    private ShopNetwork() {}

    @SubscribeEvent public static void advancementChanged(net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof ShopMenu menu)
            menu.invalidateCatalog();
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PriceAdminNetwork.register(event);
        event.registrar("7")
                .playToServer(ShopActionPayload.TYPE, ShopActionPayload.STREAM_CODEC, ShopNetwork::handleAction)
                .playToClient(ShopSyncPayload.TYPE, ShopSyncPayload.STREAM_CODEC,
                        (p, c) -> c.enqueueWork(() -> ClientShopCache.update(p)));
    }

    private static void handleAction(ShopActionPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ShopMenu menu)
                    || menu.containerId != p.containerId() || !menu.stillValid(player)) return;
            ShopService shop = TeamEconomyMod.get().shop();
            if (shop == null) return;
            if (menu.boxesOnly() != (p.kind() == ShopActionPayload.Kind.BUY_BOX)) return;
            if (!menu.acceptRequest(p.requestId(), player.level().getGameTime())) {
                sendSync(player, "message.teamecon.busy", "");
                return;
            }
            if(p.kind()==ShopActionPayload.Kind.OPEN_TERMINAL){
                if(menu.isRemote())com.evolt.teamecon.casino.TerminalItem.open(player);
                return;
            }
            ShopService.Result result = switch (p.kind()) {
                case OPEN_TERMINAL -> throw new IllegalStateException();
                case BUY_ITEM -> shop.buyItem(player, shop.pricesItem(p.target()), p.amount());
                case BUY_BOX -> shop.buyBlindBox(player, p.target(), p.amount());
                case ENCHANT -> shop.buyEnchant(player, p.target());
                case SELL_STACK -> {
                    var sold = TeamEconomyMod.get().economy().sellBatch(player, menu.sale());
                    yield new ShopService.Result(sold.outcome() == com.evolt.teamecon.economy.EconomyService.Outcome.OK
                            ? ShopService.Outcome.OK : ShopService.Outcome.BAD_ITEM, 0,
                            sold.outcome() == com.evolt.teamecon.economy.EconomyService.Outcome.OK ? "command.teamecon.sell_ok"
                            : sold.outcome() == com.evolt.teamecon.economy.EconomyService.Outcome.BALANCE_FULL
                            ? "message.teamecon.balance_full" : "command.teamecon.sell_not_priced",
                            sold.soldCount() + "," + sold.total());
                }
                case BUY_TICKET -> shop.buyTicket(player, p.target(), p.amount());
                case UPGRADE_LEVEL -> shop.upgradeLevel(player, p.amount());
            };
            player.containerMenu.broadcastChanges();
            // Also refresh the hotbar while the inventory slots are hidden on buying pages.
            if (result.outcome() == ShopService.Outcome.OK && p.kind() != ShopActionPayload.Kind.UPGRADE_LEVEL)
                player.inventoryMenu.sendAllDataToRemote();
            String key;
            String args;
            if (result.outcome() == ShopService.Outcome.OK) {
                key = result.detailKey().isEmpty() ? "command.teamecon.shop_charged" : result.detailKey();
                args = result.detailKey().isEmpty() ? String.valueOf(result.charged()) : result.detailArg();
                if (p.kind() != ShopActionPayload.Kind.SELL_STACK && menu.pos() != null && player.level().getBlockEntity(menu.pos()) instanceof ShopMachineBlockEntity machine) {
                    String icon = switch (p.kind()) {
                        case OPEN_TERMINAL -> "minecraft:air";
                        case BUY_ITEM -> p.target();
                        case BUY_TICKET -> "teamecon:scratch_card_" + p.target();
                        case UPGRADE_LEVEL -> "minecraft:experience_bottle";
                        case ENCHANT -> "minecraft:enchanted_book";
                        case SELL_STACK -> "minecraft:air";
                        case BUY_BOX -> result.rewards().isEmpty() ? "minecraft:chest" : result.rewards().split(",")[0];
                    };
                    machine.dispense(icon);
                }
            } else if (!result.detailKey().isEmpty()) {
                key = result.detailKey();
                args = result.detailArg();
                menu.invalidateCatalog();
            } else {
                key = switch (result.outcome()) {
                    case NO_FUNDS -> "command.teamecon.shop_no_funds";
                    case NO_SPACE -> "command.teamecon.shop_no_space";
                    case NO_POOL -> "command.teamecon.shop_no_pool";
                    case STAGE_LOCKED -> "command.teamecon.shop_stage_locked";
                    case NO_ENCHANT -> "command.teamecon.shop_no_enchant";
                    default -> "command.teamecon.shop_bad_item";
                };
                args = String.valueOf(result.charged());
            }
            sendSync(player, key, args, result.rewards());
            player.displayClientMessage(ModNetwork.formatMessage(key, args), true);
        });
    }

    public static void sendSync(ServerPlayer player) { sendSync(player, "", ""); }

    public static void sendSync(ServerPlayer player, String message, String args) {
        sendSync(player, message, args, "");
    }

    private static void sendSync(ServerPlayer player, String message, String args, String rewards) {
        if (!(player.containerMenu instanceof ShopMenu menu)) return;
        ShopService shop = TeamEconomyMod.get().shop();
        var economy = TeamEconomyMod.get().economy();
        if (shop == null || economy == null) return;
        ProgressionNetwork.send(player, false);
        long balance = economy.manager().getBalance(TeamUtil.walletKey(player.getServer(), player.getUUID()));
        long quote = economy.saleQuote(player, menu.sale());
        String name = ModNetwork.walletName(player);
        if (menu.catalogSent()) {
            PacketDistributor.sendToPlayer(player, new ShopSyncPayload(menu.containerId, balance, name, quote,
                    -1, 0, "", "", "", message, args, rewards));
            return;
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String item : menu.boxesOnly() ? List.<String>of() : shop.itemCatalog()) {
            var access = shop.itemAccess(player, item);
            String row = item + "," + shop.unitBuyPrice(item) + "," + (access.unlocked() ? 1 : 0) + "," + access.reason();
            if (row.length() > 512) continue;
            if (chunk.length() + row.length() + 1 > 12000) { chunks.add(chunk.toString()); chunk.setLength(0); }
            if (!chunk.isEmpty()) chunk.append(";");
            chunk.append(row);
        }
        if (!chunk.isEmpty() || chunks.isEmpty()) chunks.add(chunk.toString());
        List<String> boxChunks = new ArrayList<>();
        StringBuilder boxChunk = new StringBuilder();
        if (menu.boxesOnly()) for (var pool : shop.blindBoxes().all()) {
            var access = shop.boxAccess(player, pool);
            for (var prize : pool.entries()) {
                String row = pool.id() + "," + pool.price() + "," + access.reason() + "," + (access.unlocked() ? 1 : 0)
                        + "," + prize.itemKey() + "," + prize.count() + "," + prize.weight();
                if (boxChunk.length() + row.length() + 1 > 12000) { boxChunks.add(boxChunk.toString()); boxChunk.setLength(0); }
                if (!boxChunk.isEmpty()) boxChunk.append(";");
                boxChunk.append(row);
            }
        }
        if (!boxChunk.isEmpty()) boxChunks.add(boxChunk.toString());
        String books = menu.boxesOnly() ? "" : shop.enchants().all().stream().map(offer -> {
            var access = shop.enchantAccess(player, offer);
            return offer.id() + "," + offer.enchantmentId() + "," + offer.level() + "," + offer.price() + "," +
                    access.reason() + "," + (access.unlocked() ? 1 : 0);
        }).collect(Collectors.joining(";"));
        int count = Math.max(chunks.size(), boxChunks.size());
        for (int i = 0; i < count; i++)
            PacketDistributor.sendToPlayer(player, new ShopSyncPayload(menu.containerId, balance, name, quote,
                    i, count, i < chunks.size() ? chunks.get(i) : "", i < boxChunks.size() ? boxChunks.get(i) : "",
                    i == 0 ? books : "", message, args, rewards));
        menu.markCatalogSent();
    }
}
