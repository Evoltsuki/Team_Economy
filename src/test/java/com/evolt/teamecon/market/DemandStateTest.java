package com.evolt.teamecon.market;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Math balance checks for the demand-decay market. Config defaults used here:
 * decayPerUnit=0.01, minPriceFactor=0.05, recoverySecondsPerUnit=30.
 */
class DemandStateTest {
    @Test
    void zeroCapMeansUnlimitedDemandTracking() {
        DemandState market = new DemandState(new MarketParams(.01, .05, 30, 0, true));
        market.addDip(SCOPE, GROUP, 100, NOW);
        assertEquals(100, market.currentDip(SCOPE, GROUP, NOW), 1e-9);
    }

    @Test
    void splittingAtADemandCapDoesNotChangeThePrice() {
        MarketParams tuning = new MarketParams(.01, .05, 30, 2, true);
        DemandState batch = new DemandState(tuning), singles = new DemandState(tuning);
        double total = 0;
        for (int i = 0; i < 10; i++) {
            total += singles.factorIntegral(SCOPE, GROUP, 1, NOW);
            singles.addDip(SCOPE, GROUP, 1, NOW);
        }
        assertEquals(batch.factorIntegral(SCOPE, GROUP, 10, NOW), total, 1e-9);
    }

    @Test
    void fullDecayStillHasAFinitePriceFloor() {
        DemandState market = new DemandState(new MarketParams(1, .05, 30, 5000, true));
        assertEquals(.05, market.factorIntegral(SCOPE, GROUP, 1, NOW), 1e-9);
    }

    private static final UUID TEAM = UUID.randomUUID();
    private static final MarketParams PARAMS = new MarketParams(0.01D, 0.05D, 30D, 5000, true);
    private static final String GROUP = "minecraft:iron_ingot";
    private static final long NOW = 1_000_000_000_000L;
    private static final UUID SCOPE = TEAM; // per-team scope, since params.demandScopeIsTeam() is true
    private static final double MIN_PRICE_FACTOR = 0.05D;

    private DemandState fresh() {
        return new DemandState(PARAMS);
    }

    /**
     * Selling N items in one order must cost the market exactly the same as N single-item
     * orders sold in sequence: marginal prices only depend on cumulative units.
     */
    @Test
    void batchEqualsSequentialSingles() {
        DemandState batch = fresh();
        DemandState sequential = fresh();
        int n = 64;

        double batchSum = batch.factorIntegral(SCOPE, GROUP, 1D * n, NOW);
        batch.addDip(SCOPE, GROUP, 1D * n, NOW);

        double sequentialSum = 0D;
        for (int i = 0; i < n; i++) {
            sequentialSum += sequential.factorIntegral(SCOPE, GROUP, 1D, NOW);
            sequential.addDip(SCOPE, GROUP, 1D, NOW);
        }

        assertEquals(batchSum, sequentialSum, 1e-9, "splitting an order must not change the payout");
        assertEquals(batch.currentDip(SCOPE, GROUP, NOW), sequential.currentDip(SCOPE, GROUP, NOW), 1e-9);
    }

    /** Same check for fractional items: 1 block (9 units) vs 9 ingots. */
    @Test
    void blockEqualsIngots() {
        DemandState blocks = fresh();
        DemandState ingots = fresh();

        double blockSum = blocks.factorIntegral(SCOPE, GROUP, 9D, NOW);
        blocks.addDip(SCOPE, GROUP, 9D, NOW);

        double ingotSum = 0D;
        for (int i = 0; i < 9; i++) {
            ingotSum += ingots.factorIntegral(SCOPE, GROUP, 1D, NOW);
            ingots.addDip(SCOPE, GROUP, 1D, NOW);
        }

        assertEquals(blockSum, ingotSum, 1e-9, "converting blocks to ingots must not change value");
    }

    /** Recrafting must not reset demand: a crafting action is never a sale. */
    @Test
    void craftingDoesNotResetDemand() {
        DemandState state = fresh();
        state.addDip(SCOPE, GROUP, 9D, NOW);
        double before = state.currentDip(SCOPE, GROUP, NOW);
        assertTrue(before > 0D);
        double after = state.currentDip(SCOPE, GROUP, NOW);
        assertEquals(before, after, 1e-9);
    }

    /** Demand recovers over real time, back to full price. */
    @Test
    void demandRecoversOverTime() {
        DemandState state = fresh();
        state.addDip(SCOPE, GROUP, 10D, NOW);
        assertEquals(10D, state.currentDip(SCOPE, GROUP, NOW), 1e-9);
        long later = NOW + (long) (PARAMS.recoverySecondsPerUnit() * 10 * 1000);
        assertEquals(0D, state.currentDip(SCOPE, GROUP, later), 1e-6, "dip should fully recover");
    }

    /** Prices fall with volume but never below the configured floor. */
    @Test
    void priceFallsThenFloors() {
        DemandState state = fresh();
        double first = state.factorIntegral(SCOPE, GROUP, 1D, NOW);
        assertTrue(first > 0.99D && first <= 1D, "first unit is near full price: " + first);
        state.addDip(SCOPE, GROUP, 1_000_000D, NOW);
        double floor = state.factorIntegral(SCOPE, GROUP, 1D, NOW);
        assertTrue(floor >= MIN_PRICE_FACTOR - 1e-9, "price must not fall below the floor");
        assertTrue(floor <= 1D, "price must not exceed full price");
    }
}
