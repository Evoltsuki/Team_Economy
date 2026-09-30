package com.evolt.teamecon.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Balance checks for the shop. Blind boxes must never be expected to pay out more than they
 * cost, and the weighted draw must respect the configured weights, so a box can never be a
 * money printer even when the config file is edited.
 */
class ShopMathTest {

    private static final double CAP = 1.0D;
    private static final double EPS = 1e-9;

    private ShopPool pool(String id, long price, String... entries) {
        ShopPool pool = new ShopPool();
        pool.describe(id, price, "");
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            pool.addEntry(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        return pool;
    }

    /** A box priced above its expected value is safe. */
    @Test
    void cheapBoxHasHouseEdge() {
        ShopPool pool = pool("cheap", 200L, "minecraft:iron_ingot|2|50", "minecraft:air|0|50");
        // iron_ingot sells for 8 in the default base table, so EV = 0.5 * 8 * 2 = 8.
        double ev = expectedWithIronOnly(pool);
        assertTrue(ev <= pool.price() * CAP + EPS,
                "box EV " + ev + " must stay under its price");
    }

    /** Expected value only counts entries that actually carry a price. */
    private double expectedWithIronOnly(ShopPool pool) {
        int total = pool.totalWeight();
        double ev = 0D;
        for (ShopPool.Entry entry : pool.entries()) {
            double unit = entry.itemKey().equals("minecraft:iron_ingot") ? 8D : 0D;
            ev += ((double) entry.weight() / total) * unit * entry.count();
        }
        return ev;
    }

    /** Weights turn into probabilities, so a lopsided box still draws its rare item rarely. */
    @Test
    void weightsAreProbabilities() {
        ShopPool pool = pool("lopsided", 100L, "minecraft:iron_ingot|1|1", "minecraft:air|0|99");
        assertEquals(100, pool.totalWeight());
        assertEquals(1D / 100D, weightFraction(pool, "minecraft:iron_ingot"), EPS);
        assertEquals(99D / 100D, weightFraction(pool, "minecraft:air"), EPS);
    }

    private double weightFraction(ShopPool pool, String itemKey) {
        for (ShopPool.Entry entry : pool.entries()) {
            if (entry.itemKey().equals(itemKey)) {
                return (double) entry.weight() / pool.totalWeight();
            }
        }
        return 0D;
    }

    /** An empty pool refuses to operate instead of charging for nothing. */
    @Test
    void emptyPoolIsNotReady() {
        ShopPool pool = pool("empty", 100L);
        assertEquals(0, pool.entries().size());
        assertFalse(pool.ready());
    }
}