package com.evolt.teamecon.price;

import com.evolt.teamecon.config.TeConfig;
import net.minecraft.world.item.Rarity;

/**
 * Value floor for everything the recipe graph cannot reach: mob drops, loot, fishing
 * hauls and quest finds - anything obtained rather than crafted. Vanilla rarity is the
 * scale, so an apple is worth less than a golden apple and an enchanted golden apple
 * more than both.
 * <p>
 * No arbitrage loop can start here. A fallback-priced item has no recipe that produces
 * it, so this value only ever flows upward into things that consume it, and buying from
 * the shop still costs the sell value times the buy markup.
 */
public record RarityFallback(boolean enabled, long common, long uncommon, long rare, long epic) {

    public long priceOf(Rarity rarity) {
        return rarity == null ? 0L : priceByName(rarity.name());
    }

    /**
     * Name-keyed so a mod extending the rarity enum degrades to "no floor" instead of
     * breaking price resolution, and so the mapping itself is testable without a game
     * registry: those items opt in through the base price config.
     */
    public long priceByName(String rarityName) {
        if (!enabled || rarityName == null) {
            return 0L;
        }
        return switch (rarityName) {
            case "COMMON" -> common;
            case "UNCOMMON" -> uncommon;
            case "RARE" -> rare;
            case "EPIC" -> epic;
            default -> 0L;
        };
    }

    public static RarityFallback defaults() {
        return new RarityFallback(true, 1L, 8L, 128L, 1024L);
    }

    public static RarityFallback fromConfig() {
        return new RarityFallback(
                TeConfig.PRICING.rarityFallbackEnabled.get(),
                TeConfig.PRICING.rarityFallbackCommon.get(),
                TeConfig.PRICING.rarityFallbackUncommon.get(),
                TeConfig.PRICING.rarityFallbackRare.get(),
                TeConfig.PRICING.rarityFallbackEpic.get());
    }
}