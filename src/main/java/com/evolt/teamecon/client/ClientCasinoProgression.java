package com.evolt.teamecon.client;

import com.evolt.teamecon.gambling.CasinoProgression;
import com.evolt.teamecon.gambling.RiskTier;
import com.evolt.teamecon.network.payloads.ProgressionSyncPayload;
import com.evolt.teamecon.scratch.ScratchKind;
import com.google.gson.*;
import java.util.*;

/** Never substitutes local defaults for server rules. Empty data disables purchase/game buttons. */
public final class ClientCasinoProgression {
    public record GameInfo(boolean enabled, long maxBet, double minReturn, double maxReturn, double maxPayout, List<RiskTier> tiers) {}
    private static final GameInfo EMPTY = new GameInfo(false, 0, 0, 0, 0, List.of());
    private static final Map<String, GameInfo> games = new HashMap<>();
    private static CasinoProgression rules;
    private static int level = 1;
    private static boolean valid;
    private static double expectedCap;
    private static long revision;
    private ClientCasinoProgression() {}
    public static void update(ProgressionSyncPayload payload) {
        try {
            CasinoProgression next = CasinoProgression.fromJson(JsonParser.parseString(payload.rules()).getAsJsonObject());
            JsonObject stats = JsonParser.parseString(payload.statistics()).getAsJsonObject();
            Map<String, GameInfo> loaded = new HashMap<>();
            for (String id : CasinoProgression.GAMES) {
                JsonObject info = stats.getAsJsonObject(id); List<RiskTier> tiers = new ArrayList<>();
                for (JsonElement element : info.getAsJsonArray("tiers")) {
                    JsonObject t = element.getAsJsonObject();
                    tiers.add(new RiskTier(t.get("name").getAsString(), t.get("chance").getAsDouble(), t.get("gain").getAsDouble()));
                }
                loaded.put(id, new GameInfo(info.get("enabled").getAsBoolean(), info.get("maxBet").getAsLong(),
                        info.get("minReturn").getAsDouble(), info.get("maxReturn").getAsDouble(), info.get("maxPayout").getAsDouble(), List.copyOf(tiers)));
            }
            rules = next; games.clear(); games.putAll(loaded); level = payload.level(); valid = payload.valid();
            expectedCap = stats.get("expectedCap").getAsDouble(); revision++;
        } catch (RuntimeException e) { clear(); }
    }
    public static boolean ready() { return rules != null; }
    public static boolean valid() { return ready() && valid; }
    public static int level() { return level; }
    public static long revision() { return revision; }
    public static CasinoProgression rules() { return rules; }
    public static GameInfo game(String id) { return games.getOrDefault(id, EMPTY); }
    public static boolean canPlay(String id) { return valid() && rules.profile(id) != null && level >= rules.profile(id).requiredLevel() && game(id).enabled(); }
    public static boolean canBuy(ScratchKind card) { return valid() && level >= rules.cardLevel(card) && card.expectedFactor() <= expectedCap + 1e-9; }
    public static void clear() { rules = null; games.clear(); level = 1; valid = false; expectedCap = 0; revision++; }
}
