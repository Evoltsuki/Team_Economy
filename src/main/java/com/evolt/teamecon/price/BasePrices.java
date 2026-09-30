package com.evolt.teamecon.price;

import com.evolt.teamecon.util.ModLogger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.evolt.teamecon.economy.MoneyMath;

/**
 * Base resource prices: hand-configured entry points of the economy (raw ores, ingots,
 * gems, crops...). Everything craftable is derived from these through {@link RecipePricer},
 * so only the raw materials need manual pricing.
 */
public final class BasePrices {

    private final Map<String, Long> prices = new HashMap<>();

    public long get(String itemKey) {
        return prices.getOrDefault(itemKey, 0L);
    }

    public boolean has(String itemKey) {
        return prices.containsKey(itemKey);
    }

    public void put(String itemKey, long price) {
        if (ResourceLocation.tryParse(itemKey) == null || price < 0 || price > MoneyMath.MAX_PRICE)
            throw new IllegalArgumentException("Invalid base price: " + itemKey);
        prices.put(itemKey, price);
    }

    public Map<String, Long> all() {
        return prices;
    }

    public void load(Path file) {
        prices.clear();
        defaults();
        if (!Files.exists(file)) {
            ModLogger.info("Base price file not found, using built-in defaults: {}", file);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                        .toJson(new java.util.TreeMap<>(prices)) + "\n");
            } catch (IOException e) {
                ModLogger.error("Could not write default base prices", e);
            }
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element instanceof JsonObject obj) {
                obj.entrySet().forEach(e -> {
                    try {
                        put(e.getKey(), e.getValue().getAsLong());
                    } catch (RuntimeException ex) {
                        ModLogger.warn("Bad base price for {}: {}", e.getKey(), e.getValue());
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            ModLogger.error("Failed to read base prices", e);
        }
        ModLogger.info("Loaded {} base prices from {}", prices.size(), file);
    }

    /** Fallback prices so the economy has sensible anchors before any config is written. */
    private void defaults() {
        // Vanilla raw/near-raw materials; items craftable from these are priced by recipe.
        put("minecraft:iron_ingot", 8);
        put("minecraft:gold_ingot", 64);
        put("minecraft:diamond", 256);
        put("minecraft:emerald", 96);
        put("minecraft:lapis_lazuli", 6);
        put("minecraft:redstone", 4);
        put("minecraft:coal", 2);
        put("minecraft:copper_ingot", 4);
        put("minecraft:netherite_ingot", 2048);
        put("minecraft:quartz", 3);
        put("minecraft:amethyst_shard", 12);
        put("minecraft:wheat", 2);
        put("minecraft:carrot", 2);
        put("minecraft:potato", 2);
        put("minecraft:beetroot", 2);
        put("minecraft:sugar_cane", 2);
        put("minecraft:egg", 2);
        put("minecraft:leather", 6);
        put("minecraft:feather", 2);
        put("minecraft:string", 3);
        put("minecraft:gunpowder", 8);
        put("minecraft:glowstone_dust", 6);
        put("minecraft:blaze_rod", 32);
        put("minecraft:ender_pearl", 64);
        put("minecraft:slime_ball", 6);
        put("minecraft:bone", 3);
        put("minecraft:spider_eye", 4);
        put("minecraft:rotten_flesh", 1);
        put("minecraft:oak_log", 2);
        put("minecraft:stone", 1);
        put("minecraft:cobblestone", 1);
        put("minecraft:netherrack", 1);
        put("minecraft:obsidian", 16);
        // Acquisition difficulty matters more than the tooltip's rarity colour.
        put("minecraft:nether_star", 8192);
        put("minecraft:wither_skeleton_skull", 1536);
        put("minecraft:dragon_egg", 65536);
        put("minecraft:dragon_head", 8192);
        put("minecraft:elytra", 16384);
        put("minecraft:totem_of_undying", 2048);
        put("minecraft:enchanted_golden_apple", 4096);
        put("minecraft:heart_of_the_sea", 1536);
        put("minecraft:nautilus_shell", 96);
        put("minecraft:shulker_shell", 384);
        put("minecraft:ghast_tear", 192);
        put("minecraft:phantom_membrane", 48);
        put("minecraft:echo_shard", 384);
        put("minecraft:disc_fragment_5", 1024);
        put("minecraft:breeze_rod", 128);
        put("minecraft:heavy_core", 16384);
        put("minecraft:trial_key", 128);
        put("minecraft:ominous_trial_key", 512);
        put("minecraft:sniffer_egg", 4096);
        put("minecraft:sponge", 256);
        put("minecraft:trident", 4096);
        put("minecraft:turtle_scute", 96);
        put("minecraft:armadillo_scute", 32);
        put("minecraft:netherite_scrap", 384);
        put("minecraft:enchanted_book", 64);
        put("minecraft:apple", 4);
        put("minecraft:nether_wart", 8);
        put("minecraft:rabbit_foot", 64);
        put("minecraft:rabbit_hide", 3);
        put("minecraft:honeycomb", 8);
    }

    public static String key(ResourceLocation id) {
        return id.toString();
    }
}
