package com.evolt.teamecon.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side price table, populated from the periodic server snapshot. Used by the tooltip
 * handler to show the auto-valued sell price under an item.
 */
public final class ClientPriceCache {

    private static volatile Map<String, Long> prices = new ConcurrentHashMap<>();

    private ClientPriceCache() {
    }

    public static void update(Map<String, Long> snapshot) {
        prices = snapshot instanceof ConcurrentHashMap ? snapshot : new ConcurrentHashMap<>(snapshot);
    }

    /** Unit price of an item key, or -1 when the client has no price for it yet. */
    public static long priceOf(String itemKey) {
        Long value = prices.get(itemKey);
        return value == null ? -1L : value;
    }

    public static int size() {
        return prices.size();
    }

    public static void clear() { prices = new ConcurrentHashMap<>(); }

    /** Read-only view over the whole table, for screens that label many items at once. */
    public static PriceTable table() {
        return new PriceTable(prices);
    }

    /** Snapshot view that answers price lookups without exposing the live map. */
    public record PriceTable(Map<String, Long> prices) {
        public Long priceOf(String itemKey) {
            return prices.get(itemKey);
        }

        public boolean isEmpty() {
            return prices.isEmpty();
        }
    }
}
