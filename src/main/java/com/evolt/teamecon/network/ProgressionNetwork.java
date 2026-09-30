package com.evolt.teamecon.network;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.gambling.CasinoProgression;
import com.evolt.teamecon.network.payloads.ProgressionSyncPayload;
import com.evolt.teamecon.shop.ShopMenu;
import com.evolt.teamecon.team.TeamUtil;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Sends actual server odds on login, wallet change, upgrade and config reload, including outside menus. */
@EventBusSubscriber(modid = "teamecon")
public final class ProgressionNetwork {
    private record Stamp(UUID wallet, int level, CasinoProgression rules, long minBet, long maxBet, double cap, double multiplier) {}
    private static final Map<UUID, Stamp> SENT = new HashMap<>();
    private ProgressionNetwork() {}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { SENT.remove(event.getEntity().getUUID()); }
    public static void send(ServerPlayer player, boolean force) {
        var mod = TeamEconomyMod.get();
        if (mod == null || mod.economy() == null || mod.gambling() == null || mod.casinoProgression() == null) return;
        var games = mod.gambling(); var rules = mod.casinoProgression();
        UUID wallet = TeamUtil.walletKey(player.getServer(), player.getUUID());
        int level = mod.economy().manager().casinoLevel(wallet);
        Stamp stamp = new Stamp(wallet, level, rules, games.minBet(), games.maxBet(), games.expectedCap(), games.maxMultiplier());
        if (!force && stamp.equals(SENT.get(player.getUUID()))) return;
        SENT.put(player.getUUID(), stamp);
        if (player.containerMenu instanceof ShopMenu menu) menu.invalidateCatalog();
        JsonObject statistics = new JsonObject();
        statistics.addProperty("expectedCap", games.expectedCap());
        for (String id : CasinoProgression.GAMES) {
            var odds = games.odds(id); JsonObject info = new JsonObject();
            info.addProperty("enabled", games.enabled(id)); info.addProperty("maxBet", games.maxBet(id, wallet));
            info.addProperty("minReturn", odds.minReturn()); info.addProperty("maxReturn", odds.maxReturn());
            info.addProperty("maxPayout", odds.maxPayout());
            JsonArray tiers = new JsonArray();
            if (id.equals("penguin") || id.equals("multiplier")) for (var tier : games.effectiveTiers(id)) {
                JsonObject t = new JsonObject(); t.addProperty("name", tier.name());
                t.addProperty("chance", tier.successChance()); t.addProperty("gain", tier.gain()); tiers.add(t);
            }
            info.add("tiers", tiers); statistics.add(id, info);
        }
        PacketDistributor.sendToPlayer(player, new ProgressionSyncPayload(level, rules.valid(), rules.toJson().toString(), statistics.toString()));
    }
}
