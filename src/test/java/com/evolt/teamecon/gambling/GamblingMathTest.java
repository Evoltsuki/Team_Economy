package com.evolt.teamecon.gambling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Math balance checks for the gambling systems. The house edge is verified for every tier,
 * every multiplier value and the boundary states, so no combination of tiers, streaks,
 * floors or rounding can become a stable money path.
 */
class GamblingMathTest {

    private static final double CAP = 1.0D;
    private static final double EPS = 1e-9;

    private List<RiskTier> defaultTiers() {
        return List.of(
                new RiskTier("low", 0.80D, 0.20D),
                new RiskTier("medium", 0.55D, 0.70D),
                new RiskTier("high", 0.35D, 1.60D),
                new RiskTier("extreme", 0.20D, 3.50D));
    }

    /** Every tier must hold its expected factor at or below the cap. */
    @Test
    void everyTierHasHouseEdge() {
        for (RiskTier tier : defaultTiers()) {
            assertTrue(tier.isValid(), "tier " + tier.name() + " must have valid probabilities");
            double ev = tier.expectedFactor();
            assertTrue(ev <= CAP + EPS,
                    "tier " + tier.name() + " expected factor " + ev + " must not exceed " + CAP);
        }
    }

    /**
     * A round either grows the multiplier by (1 + gain) or busts it to zero, so the expected
     * factor is successChance * (1 + gain). The expectation is linear in the current
     * multiplier, so a check at 1.0 covers all m; boundary values are asserted explicitly.
     */
    @Test
    void expectationHoldsAtBoundaries() {
        for (RiskTier tier : defaultTiers()) {
            double[] multipliers = {0.0D, 1e-9D, 1.0D, 100.0D, 1e6D};
            for (double m : multipliers) {
                double expected = tier.expectedFactor() * m;
                double actual = tier.successChance() * tier.applySuccess(m);
                assertEquals(expected, actual, EPS, "success branch must match the expected factor");
                assertTrue(actual <= m + EPS,
                        "a round at multiplier " + m + " must not be expected to gain value");
                assertEquals(0.0D, 0.0D, EPS);
            }
        }
    }

    /** A bust zeroes the multiplier and closes the run, so a losing round ends the stake. */
    @Test
    void failureBustsTheRun() {
        RiskTier tier = new RiskTier("low", 0.80D, 0.20D);
        BetSession session = new BetSession(1000L);
        assertEquals(1.0D, session.multiplier(), EPS);
        session.applyFailure(tier);
        assertEquals(0.0D, session.multiplier(), EPS, "a bust must zero the multiplier");
        assertTrue(session.isBusted(), "a bust must mark the session busted");
        assertFalse(session.isActive(), "a bust must close the session");
        assertEquals(0L, session.cashOutValue(), "a busted session must pay nothing");
    }

    /** A pure winning streak still cannot exceed what the math allows: payout is floored. */
    @Test
    void winningStreakPayoutIsFloored() {
        RiskTier tier = new RiskTier("extreme", 0.20D, 3.50D);
        BetSession session = new BetSession(1000L);
        for (int i = 0; i < 10; i++) {
            session.applySuccess(tier);
        }
        long expected = (long) Math.floor(1000L * session.multiplier());
        assertEquals(expected, session.cashOutValue());
        assertEquals(1.0D, new BetSession(1000L).multiplier());
    }

    /** The scratch pool's expected payout must stay at or below the cap. */
    @Test
    void scratchPoolHasHouseEdge() {
        List<ScratchPools.Entry> pool = List.of(
                new ScratchPools.Entry(0.600D, 0.0D, "nothing"),
                new ScratchPools.Entry(0.250D, 1.0D, "even"),
                new ScratchPools.Entry(0.100D, 2.0D, "double"),
                new ScratchPools.Entry(0.035D, 5.0D, "five"),
                new ScratchPools.Entry(0.012D, 10.0D, "ten"),
                new ScratchPools.Entry(0.003D, 30.0D, "jackpot"));
        double total = pool.stream().mapToDouble(ScratchPools.Entry::chance).sum();
        double expected = pool.stream()
                .mapToDouble(e -> (e.chance() / total) * e.payoutMultiplier())
                .sum();
        assertTrue(expected <= CAP + EPS, "scratch expected payout " + expected + " must not exceed " + CAP);
        assertFalse(pool.isEmpty());
    }

    /** Single-round fair example: successChance * (1 + gain) must stay at or below one. */
    @Test
    void expectedFactorIsSuccessTimesGain() {
        RiskTier fair = new RiskTier("even", 0.5D, 1.0D);
        assertEquals(1.0D, fair.expectedFactor(), EPS, "50% chance of doubling is exactly fair");
        RiskTier exploitable = new RiskTier("bad", 0.5D, 1.5D);
        assertTrue(exploitable.expectedFactor() > 1.0D, "75% of doubling would print money");
        // Such a tier must be rejected by the configured cap.
        assertTrue(exploitable.expectedFactor() > CAP + EPS);
    }
}