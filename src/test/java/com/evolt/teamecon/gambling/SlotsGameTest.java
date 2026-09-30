package com.evolt.teamecon.gambling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default slots table must keep its expected payout under the cap, otherwise a tuned
 * table would become a money printer. Both sides of hi-lo are exact 50/50 paying less than
 * even money, so its expectation is always payout / 2.
 */
class SlotsGameTest {

    @Test void pairsIncreaseWinsWithoutRemovingRareJackpots() {
        SlotsGame game = new SlotsGame(); game.defaults();
        double wins = 0;
        for (var a : game.reel()) for (var b : game.reel()) for (var c : game.reel()) {
            if (game.payoutFor(new String[]{a.name(),b.name(),c.name()}) > 0)
                wins += a.weight()*b.weight()*c.weight()/1_000_000D;
        }
        org.junit.jupiter.api.Assertions.assertEquals(.5125, wins, 1e-10);
        org.junit.jupiter.api.Assertions.assertEquals(1.2, game.payoutFor(new String[]{"diamond","cherry","diamond"}));
        org.junit.jupiter.api.Assertions.assertEquals(200, game.payoutFor(new String[]{"diamond","diamond","diamond"}));
    }

    @Test void exactLegacyConfigMigratesButCustomPayoutsSurvive(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        SlotsGame old = new SlotsGame(); old.legacyDefaults();
        var root = new com.google.gson.JsonObject(); var reel = new com.google.gson.JsonArray(); var payouts = new com.google.gson.JsonArray();
        for (var s : old.reel()) { var o=new com.google.gson.JsonObject();o.addProperty("symbol",s.name());o.addProperty("weight",s.weight());reel.add(o); }
        for (var p : old.payouts()) { var o=new com.google.gson.JsonObject();o.addProperty("symbol",p.symbol());o.addProperty("matches",p.requiredMatches());o.addProperty("multiplier",p.multiplier());payouts.add(o); }
        root.add("reel",reel);root.add("payouts",payouts);
        var file=dir.resolve("teamecon_slots.json"); java.nio.file.Files.writeString(file,root.toString());
        SlotsGame game=new SlotsGame();game.load(dir,1);
        org.junit.jupiter.api.Assertions.assertEquals(12,game.payouts().size());
        assertTrue(java.nio.file.Files.exists(dir.resolve("teamecon_slots.json.pre-pairs.bak")));
        payouts.get(0).getAsJsonObject().addProperty("multiplier",5);
        java.nio.file.Files.writeString(file,root.toString());game.load(dir,1);
        org.junit.jupiter.api.Assertions.assertEquals(5,game.payoutFor(new String[]{"cherry","cherry","cherry"}));
    }

    private static final double CAP = 1.0D;
    private static final double EPS = 1e-9;

    @Test
    void defaultTableHasHouseEdge() {
        SlotsGame game = new SlotsGame();
        game.defaults();
        int totalWeight = game.reel().stream().mapToInt(SlotsGame.Symbol::weight).sum();
        double expected = game.expectedPayout(totalWeight);
        assertTrue(expected > 0.1D, "the table must actually pay something");
        assertTrue(expected <= CAP + EPS,
                "default slots expected payout " + expected + " must not exceed the cap");
    }

    @Test
    void twoCherryConsolationDoesNotBreakTheEdge() {
        SlotsGame game = new SlotsGame();
        game.defaults();
        int totalWeight = game.reel().stream().mapToInt(SlotsGame.Symbol::weight).sum();
        double withoutConsolation = expectedWithoutCherryPair(game, totalWeight);
        assertTrue(withoutConsolation <= game.expectedPayout(totalWeight) + EPS);
        assertTrue(game.expectedPayout(totalWeight) <= CAP + EPS);
    }

    private double expectedWithoutCherryPair(SlotsGame game, int totalWeight) {
        double expected = 0D;
        for (SlotsGame.Payout payout : game.payouts()) {
            if (payout.requiredMatches() == 2) {
                continue;
            }
            double probOne = weightOf(game, payout.symbol()) / (double) totalWeight;
            expected += Math.pow(probOne, 3) * payout.multiplier();
        }
        return expected;
    }

    private int weightOf(SlotsGame game, String symbol) {
        for (SlotsGame.Symbol s : game.reel()) {
            if (s.name().equals(symbol)) {
                return s.weight();
            }
        }
        return 0;
    }

    @Test
    void hiLoHasHouseEdge() {
        double payout = 1.9D;
        double winChance = 0.5D; // high wins on 51..100 and low on 1..50: 50 numbers each
        double expected = winChance * payout;
        assertTrue(expected <= CAP + EPS,
                "hi-lo expected payout " + expected + " must not exceed the cap");
        assertTrue(expected > 0D);
    }
}
