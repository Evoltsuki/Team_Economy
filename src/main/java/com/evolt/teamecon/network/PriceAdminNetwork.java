package com.evolt.teamecon.network;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.network.payloads.*;
import com.evolt.teamecon.price.*;
import com.evolt.teamecon.shop.ShopCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.io.IOException;
import java.util.ConcurrentModificationException;

public final class PriceAdminNetwork {
    private PriceAdminNetwork() { }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(PriceAdminActionPayload.TYPE, PriceAdminActionPayload.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer player) handle(player, p);
                }))
                .playToClient(PriceAdminSyncPayload.TYPE, PriceAdminSyncPayload.STREAM_CODEC,
                        (p, c) -> c.enqueueWork(() -> com.evolt.teamecon.client.PriceAdminScreen.receive(p)));
    }

    public static void handle(ServerPlayer player, PriceAdminActionPayload p) {
        if (!PriceAdminMenu.authorized(player, p.containerId())) return;
        var mod = TeamEconomyMod.get();
        if (mod.shop() == null) return;
        var menu = (PriceAdminMenu) player.containerMenu;
        ResourceLocation id = ResourceLocation.tryParse(p.item());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        String message = "";
        if (!menu.accept(p.sequence(), player.level().getGameTime(), p.save())) message = "busy";
        else if (p.save()) {
            try {
                mod.prices().overrides().save(p.item(), new PriceOverrides.Entry(p.buy(), p.sell()), p.revision());
                mod.shop().loadConfigs(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
                mod.prices().markDirty();
                PriceSyncHandler.broadcastNow(player.getServer());
                for (ServerPlayer member : player.getServer().getPlayerList().getPlayers()) {
                    if (member.containerMenu instanceof com.evolt.teamecon.shop.ShopMenu shop) {
                        shop.invalidateCatalog(); ShopNetwork.sendSync(member);
                    }
                }
                TeamEconomyMod.LOGGER.info("Price override by {} for {}: buy={}, sell={}", player.getGameProfile().getName(), p.item(), p.buy(), p.sell());
                message = "saved";
            } catch (ConcurrentModificationException ex) { message = "conflict"; }
            catch (IOException ex) {
                message = "io_error";
                TeamEconomyMod.LOGGER.warn("Could not persist price override", ex);
            } catch (IllegalArgumentException ex) { message = "invalid"; }
        }
        PriceService prices = mod.prices();
        var entry = prices.overrides().get(p.item());
        PacketDistributor.sendToPlayer(player, new PriceAdminSyncPayload(p.containerId(), p.sequence(), p.item(), entry.buy(), entry.sell(),
                prices.purchasable(p.item()) && !p.item().equals("minecraft:enchanted_book") ? mod.shop().unitBuyPrice(p.item()) : 0,
                prices.canSell(p.item()) ? prices.resolve(p.item()).unitPrice() : 0, prices.defaultValue(p.item()).unitPrice(),
                prices.overrides().revision(), prices.overrides().valid() && ShopCatalog.configurable(p.item()), message));
    }
}
