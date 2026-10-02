package com.evolt.teamecon.network;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.network.payloads.PriceSyncPayload;
import com.evolt.teamecon.price.PriceService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

/**
 * Pushes the price table to clients. The table is only rebuilt when it changed (new derived
 * prices, a config edit or a base-price reload) and at most once every few seconds, so an
 * idle server sends nothing.
 */
@EventBusSubscriber(modid = TeamEconomyMod.MOD_ID)
public final class PriceSyncHandler {

    private static final int TICKS_BETWEEN_BROADCASTS = 100;

    private static int tickCounter = 0;

    private PriceSyncHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < TICKS_BETWEEN_BROADCASTS) {
            return;
        }
        tickCounter = 0;
        broadcast(event.getServer(), false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        broadcast(player.getServer(), true);
    }

    private static void broadcast(MinecraftServer server, boolean force) {
        TeamEconomyMod mod = TeamEconomyMod.get();
        if (mod == null || mod.economy() == null || server == null) {
            return;
        }
        PriceService prices = mod.prices();
        if (!force && !prices.isDirty()) {
            return;
        }
        prices.clearDirty();
        Map<String, Long> snapshot = prices.saleSnapshot();
        PriceSyncPayload payload = new PriceSyncPayload(snapshot);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void broadcastNow(MinecraftServer server) { broadcast(server, true); }
}
