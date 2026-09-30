package com.evolt.teamecon.price;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cover for the rarity floor that prices items the recipe graph cannot reach. The full
 * chain (a golden apple priced from its gold content, an enchanted golden apple priced by
 * rarity) is exercised by the server smoke test, which logs example prices at startup.
 */
class PriceFallbackTest {

    private static final RarityFallback FLOOR = RarityFallback.defaults();

    @Test
    void rarityFloorMapsEachTier() {
        assertEquals(1L, FLOOR.priceByName("COMMON"));
        assertEquals(8L, FLOOR.priceByName("UNCOMMON"));
        assertEquals(128L, FLOOR.priceByName("RARE"));
        assertEquals(1024L, FLOOR.priceByName("EPIC"));
    }

    @Test
    void unknownRarityGetsNoFloor() {
        assertEquals(0L, FLOOR.priceByName("LEGENDARY"));
        assertEquals(0L, FLOOR.priceByName(null));
    }

    @Test
    void disabledFloorPricesNothing() {
        RarityFallback off = new RarityFallback(false, 1L, 8L, 128L, 1024L);
        assertEquals(0L, off.priceByName("EPIC"));
    }
}