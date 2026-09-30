package com.evolt.teamecon.network;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.CasinoMachineBlockEntity;
import com.evolt.teamecon.casino.CasinoMenu;
import com.evolt.teamecon.client.CasinoScreenData;
import com.evolt.teamecon.client.ClientPriceCache;
import com.evolt.teamecon.client.ClientShopCache;
import com.evolt.teamecon.economy.EconomyService;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.network.payloads.*;
import com.evolt.teamecon.shop.ShopMenu;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = TeamEconomyMod.MOD_ID)
public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("6")
                .playToServer(MachineBetPayload.TYPE,MachineBetPayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->{
                    if(c.player() instanceof ServerPlayer player && player.containerMenu instanceof com.evolt.teamecon.casino.MachineBetMenu menu
                            && menu.containerId==p.containerId()) {
                        boolean changed=menu.apply(player,p.factor());
                        long value=com.evolt.teamecon.casino.MachineBetMenu.multiplied(menu.base(),p.factor(),menu.limit());
                        player.closeContainer();
                        player.displayClientMessage(Component.translatable(changed?"machine.teamecon.bet":"machine.teamecon.bet_input.changed",value),true);
                    }
                }))
                .playToClient(ProgressionSyncPayload.TYPE, ProgressionSyncPayload.STREAM_CODEC,
                        (p, c) -> c.enqueueWork(() -> com.evolt.teamecon.client.ClientCasinoProgression.update(p)))
                .playToServer(CasinoActionPayload.TYPE, CasinoActionPayload.STREAM_CODEC, ModNetwork::handleAction)
                .playToServer(ScratchActionPayload.TYPE,ScratchActionPayload.STREAM_CODEC,ScratchNetwork::handle)
                .playToClient(ScratchResultPayload.TYPE,ScratchResultPayload.STREAM_CODEC,
                        (p,c)->c.enqueueWork(()->com.evolt.teamecon.client.ScratchCardScreen.receive(p)))
                .playToClient(CasinoSyncPayload.TYPE, CasinoSyncPayload.STREAM_CODEC,
                        (p, c) -> c.enqueueWork(() -> CasinoScreenData.update(p)))
                .playToClient(PriceSyncPayload.TYPE, PriceSyncPayload.STREAM_CODEC,
                        (p, c) -> c.enqueueWork(() -> ClientPriceCache.update(p.prices())))
                .playToClient(TeamBoardPayload.TYPE,TeamBoardPayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->com.evolt.teamecon.client.TeamBoardHud.update(p.json())))
                .playToClient(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC,
                        (p, c) -> c.enqueueWork(() -> {
                            CasinoScreenData.updateBalance(p.containerId(), p.balance(), p.walletName());
                            ClientShopCache.updateBalance(p.containerId(), p.balance(), p.walletName(), p.saleQuote());
                        }));
    }

    private static void handleAction(CasinoActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CasinoMenu menu)
                    || menu.containerId != payload.containerId() || !menu.stillValid(player)
                    || !menu.allows(payload.game())) return;
            GamblingService gambling = TeamEconomyMod.get().gambling();
            EconomyService economy = TeamEconomyMod.get().economy();
            if (gambling == null || economy == null) return;
            int delay = payload.action()==CasinoActionPayload.Action.CASH_OUT?0:payload.action() != CasinoActionPayload.Action.PLAY || "start".equals(payload.choice())
                    ? 4 : CasinoMachineBlockEntity.animationDuration(payload.game());
            if (!menu.acceptRequest(payload.requestId(), player.level().getGameTime(), delay)) {
                sendSync(player, payload.requestId(), "", "message.teamecon.busy", "", "");
                return;
            }
            if(payload.action()==CasinoActionPayload.Action.OPEN_SHOP){
                if(menu.isRemote())ShopMenu.open(player,null,true,java.util.Set.of("items","sell","levels","cards").contains(payload.choice())?payload.choice():"items");
                return;
            }
            UUID wallet = TeamUtil.walletKey(player.getServer(), player.getUUID());
            if (payload.action()==CasinoActionPayload.Action.BUY_CARD) {
                if(!menu.isRemote()||!"scratch".equals(payload.game()))return;
                var kind=com.evolt.teamecon.scratch.ScratchKind.byId(payload.choice());
                String key=TeamEconomyMod.get().scratchCards().buy(player,kind,(int)Math.clamp(payload.amount(),1,10000));
                sendSync(player,payload.requestId(),"scratch",key,"","");
                return;
            }
            if (payload.action()==CasinoActionPayload.Action.PLAY && "scratch".equals(payload.game())) return;
            if (payload.action() == CasinoActionPayload.Action.DEPOSIT) {
                var sold = economy.sell(player, menu.deposit().getItem(0));
                menu.deposit().setChanged();
                menu.broadcastChanges();
                sendSync(player, payload.requestId(), payload.game(),
                        sold.outcome() == EconomyService.Outcome.OK ? "message.teamecon.deposit_sold"
                                : sold.outcome() == EconomyService.Outcome.BALANCE_FULL ? "message.teamecon.balance_full" : "message.teamecon.deposit_not_priced",
                        String.valueOf(sold.total()), "");
                return;
            }
            String game = payload.game();
            BetSession before = gambling.sessionOf(player.getUUID());
            if(before!=null&&before.worldBound()) {
                sendSync(player,payload.requestId(),game,"machine.teamecon.return_to_machine","","");return;
            }
            long previousStake = before == null ? 0 : before.stake();
            GamblingService.Result result;
            if (payload.action() == CasinoActionPayload.Action.CASH_OUT) {
                result = gambling.cashOut(player.getUUID(), wallet, player.getName().getString(), game);
            } else if (game.equals("multiplier") || game.equals("penguin")) {
                result = payload.choice().equals("start")
                        ? gambling.startBet(player.getUUID(), wallet, player.getName().getString(), payload.amount(), game)
                        : gambling.playRound(player.getUUID(), wallet, game, payload.choice());
            } else {
                result = gambling.playInstant(player.getUUID(), wallet, player.getName().getString(), payload.amount(), game, payload.choice());
            }
            String key;
            String args = "";
            switch (result.status()) {
                case BUSY -> key = "busy";
                case TOO_SMALL -> { key = "bet_too_small"; args = String.valueOf(gambling.minBet()); }
                case TOO_BIG -> { key = "bet_too_big"; args = String.valueOf(gambling.maxBet(game, wallet)); }
                case LEVEL_LOCKED -> {
                    key = "requires_level";
                    var profile = gambling.progression().profile(game);
                    args = String.valueOf(profile == null ? 5 : profile.requiredLevel());
                }
                case NO_FUNDS -> key = "bet_no_funds";
                case DISABLED -> key = "game_disabled";
                case BAD_CHOICE -> key = "round_unknown_tier";
                case ALREADY_PLAYING, WRONG_GAME -> key = "bet_already_playing";
                case NO_SESSION -> key = "round_no_session";
                case TEAM_CHANGED -> key = "team_changed";
                case LIMIT -> key = "multiplier_limit";
                case ROUND_WON -> { key = game.equals("penguin") ? "penguin_landed" : "round_won"; args = formatMult(result.multiplier()); }
                case BUSTED -> { key = game.equals("penguin") ? "penguin_fell" : "round_busted"; args = String.valueOf(previousStake > 0 ? previousStake : payload.amount()); }
                case CASHED_OUT -> { key = result.extra().equals("original_wallet") ? "cashed_out_original" : "cashed_out"; args = String.valueOf(result.payout()); }
                default -> {
                    boolean won = result.payout() > 0;
                    switch (game) {
                        case "multiplier", "penguin" -> { key = "bet_placed"; args = String.valueOf(payload.amount()); }
                        case "slots" -> { key = won ? "slots_win" : "slots_lose"; args = String.valueOf(result.payout()); }
                        case "scratch" -> { key = won ? "scratch_won" : "scratch_no_prize"; args = result.payout() + "," + formatMult(result.multiplier()); }
                        case "hilo" -> { key = won ? "hilo_won" : "hilo_lose"; args = result.extra() + "," + result.payout(); }
                        default -> { key = won ? "roulette_won" : "roulette_lose"; args = result.extra() + "," + result.payout(); }
                    }
                }
            }
            if (result.accepted() && !menu.isRemote() && !payload.choice().equals("start"))
                animateMachine(player, game, result);
            String message = "message.teamecon." + key;
            sendSync(player, payload.requestId(), game, message, args, result.accepted() ? result.extra() : "");
            player.displayClientMessage(formatMessage(message, args), true);
        });
    }

    private static void animateMachine(ServerPlayer player, String game, GamblingService.Result result) {
        CasinoMenu menu = (CasinoMenu) player.containerMenu;
        if (!(player.level().getBlockEntity(menu.pos()) instanceof CasinoMachineBlockEntity machine)) return;
        String[] symbols = switch (game) {
            case "slots" -> result.extra().split(",");
            case "multiplier" -> new String[]{"mult", formatMult(result.multiplier()), ""};
            case "penguin" -> result.extra().split(",");
            case "scratch" -> {
                String[] card = result.extra().split(",");
                yield new String[]{"scratch", card.length > 0 ? card[0] : "", card.length > 1 ? card[1] : "0"};
            }
            default -> new String[]{game, result.extra(), ""};
        };
        machine.showSpin(symbols, result.payout() > 0 || result.status() == GamblingService.Status.ROUND_WON);
    }

    public static void sendOpenSync(ServerPlayer player) { sendSync(player, 0, "", "", "", ""); }

    public static void sendSync(ServerPlayer player, long request, String game, String key, String args, String extra) {
        if (!(player.containerMenu instanceof CasinoMenu menu)) return;
        GamblingService gambling = TeamEconomyMod.get().gambling();
        EconomyService economy = TeamEconomyMod.get().economy();
        if (gambling == null || economy == null) return;
        ProgressionNetwork.send(player, false);
        UUID wallet = TeamUtil.walletKey(player.getServer(), player.getUUID());
        BetSession session = gambling.sessionOf(player.getUUID());
        PacketDistributor.sendToPlayer(player, new CasinoSyncPayload(menu.containerId, request,
                economy.manager().getBalance(wallet), walletName(player),
                gambling.minBet(), gambling.maxBet(), session == null ? 1 : session.multiplier(),
                session == null ? 0 : session.stake(), session == null ? 0 : session.rounds(),
                session == null ? "" : session.game(), session == null ? 0 : session.cashOutValue(),
                session == null ? gambling.maxMultiplier() : session.maxMultiplier(), game, key, args, extra,
                gambling.effectiveTiers("multiplier").stream().map(t -> t.name() + "," + t.successChance() + "," + t.gain()).collect(Collectors.joining(";")),
                gambling.slots().payouts().stream().map(p -> p.symbol() + "," + p.requiredMatches() + "," + p.multiplier() * gambling.progression().profile("slots").payoutScale()).collect(Collectors.joining(";")),
                gambling.scratch().all().stream().map(e -> e.label() + "," + e.chance() + "," + e.payoutMultiplier()).collect(Collectors.joining(";")),
                gambling.hiLoPayout(), gambling.expectedCap()));
    }

    public static String walletName(ServerPlayer player) {
        String name = TeamUtil.displayName(player.getServer(), player.getUUID());
        if (name.equals(TeamUtil.NO_TEAM_NAMESPACE)) name = player.getName().getString();
        return name.length() > 128 ? name.substring(0, 128) : name;
    }

    /** Team members see the shared wallet change while their menus remain open. */
    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        EconomyService economy = TeamEconomyMod.get().economy();
        if (economy == null) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ProgressionNetwork.send(player, false);
            if (!(player.containerMenu instanceof CasinoMenu) && !(player.containerMenu instanceof ShopMenu)) continue;
            if (player.containerMenu instanceof ShopMenu menu && !menu.catalogSent()) {
                ShopNetwork.sendSync(player);
                continue;
            }
            UUID wallet = TeamUtil.walletKey(player.getServer(), player.getUUID());
            long quote = player.containerMenu instanceof ShopMenu shop ? economy.saleQuote(player,shop.sale()) : -1;
            PacketDistributor.sendToPlayer(player, new BalanceSyncPayload(player.containerMenu.containerId,
                    economy.manager().getBalance(wallet), walletName(player), quote));
        }
    }

    private static String formatMult(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    public static Component formatMessage(String key, String args) {
        if (key == null || key.isEmpty()) return Component.empty();
        if (key.equals("shop.teamecon.progression_locked")) return com.evolt.teamecon.shop.PurchaseRules.describe(args);
        if (args == null || args.isEmpty()) return Component.translatable(key);
        String[] raw = args.split(",");
        // A String[] assigned to Object[] still rejects Component elements at runtime.
        Object[] values = java.util.Arrays.copyOf(raw, raw.length, Object[].class);
        for (int i = 0; i < values.length; i++) {
            String text = (String) values[i];
            if (text.contains(":")) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(text);
                if (id == null) continue;
                if (key.contains("book_")) values[i] = Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath());
                else if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))
                    values[i] = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)).getHoverName();
            }
        }
        return Component.translatable(key, values);
    }
}
