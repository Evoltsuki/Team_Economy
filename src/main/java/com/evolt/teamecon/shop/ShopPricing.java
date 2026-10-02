package com.evolt.teamecon.shop;

import com.evolt.teamecon.economy.MoneyMath;
import com.evolt.teamecon.util.ModLogger;
import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Acquisition premiums affect retail only. Farm output never becomes more valuable to sell. */
public final class ShopPricing {
    public static final String FILE_NAME = "teamecon_shop_prices.json";
    private final Map<String, Long> items = new LinkedHashMap<>(), enchants = new LinkedHashMap<>();
    private boolean enabled = true;

    public ShopPricing() {
        defaults();
    }
    private void defaults() {
        items.clear(); enchants.clear(); enabled = true;
        // Retail convenience has a cost; keep the underlying farm resale anchors unchanged.
        put(8, "potato", "beetroot", "sweet_berries", "glow_berries", "dried_kelp", "cookie");
        put(8, "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "tropical_fish");
        put(24, "chorus_fruit");
        put(12, "carrot", "apple", "melon_slice");
        put(16, "beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon");
        put(24, "bread", "baked_potato", "cooked_chicken", "cooked_rabbit", "cooked_cod");
        put(32, "cooked_beef", "cooked_porkchop", "cooked_mutton", "cooked_salmon");
        put(40, "mushroom_stew", "beetroot_soup", "rabbit_stew", "suspicious_stew", "honey_bottle");
        put(48, "pumpkin_pie");
        put(96, "cake");
        put(128, "golden_carrot");
        put(8192, "axolotl_bucket");
        put(2048, "tropical_fish_bucket", "pufferfish_bucket", "tadpole_bucket");
        put(768, "cod_bucket", "salmon_bucket");
        put(4096, "suspicious_sand", "suspicious_gravel", "sculk_shrieker");
        put(4096, "ancient_debris", "netherite_scrap", "shulker_shell", "nautilus_shell", "echo_shard", "sponge", "wet_sponge");
        put(24576, "netherite_ingot", "trident", "totem_of_undying", "heart_of_the_sea", "sniffer_egg");
        put(131072, "nether_star", "beacon", "enchanted_golden_apple");
        put(262144, "elytra", "heavy_core", "mace", "dragon_egg");
        put(16384, "wither_skeleton_skull", "dragon_head", "disc_fragment_5");
        put(2048, "breeze_rod", "trial_key", "ominous_trial_key", "turtle_scute", "rabbit_foot");
        put(512, "ghast_tear", "phantom_membrane", "blaze_rod");
        put(256, "ender_pearl");
        put(8192, "shulker_box");
        put(24576, "lodestone", "conduit");
        put(10240, "turtle_helmet");
        put(32768, "recovery_compass");
        put(221184, "netherite_block");
        enchants.put("minecraft:unbreaking", 4096L);
        enchants.put("minecraft:efficiency", 8192L);
        enchants.put("minecraft:fortune", 16384L);
        enchants.put("minecraft:silk_touch", 16384L);
        enchants.put("minecraft:sharpness", 32768L);
        enchants.put("minecraft:protection", 16384L);
        enchants.put("minecraft:mending", 65536L);
        enchants.put("minecraft:soul_speed", 32768L);
        enchants.put("minecraft:swift_sneak", 65536L);
        enchants.put("minecraft:wind_burst", 131072L);
    }
    private void put(long value, String... paths) { for (String path : paths) items.put("minecraft:" + path, value); }
    private long anchored(long price, long diamond) {
        return MoneyMath.payout(price, Math.max(1D, diamond / 256D));
    }
    public long itemPrice(String id, long ordinary, long diamond) {
        if (!enabled) return ordinary;
        long minimum = items.getOrDefault(id, 0L);
        if (!items.containsKey(id) && id.startsWith("minecraft:")) {
            if (id.endsWith("_shulker_box")) minimum = 8192;
            else if (id.startsWith("minecraft:netherite_") && !id.endsWith("_scrap") && !id.endsWith("_ingot")) minimum = 32768;
            else if (id.endsWith("_smithing_template")) minimum = 16384;
            else if (id.startsWith("minecraft:music_disc_")) minimum = 8192;
            else if (id.endsWith("_pottery_sherd")) minimum = 2048;
            else if (id.endsWith("_banner_pattern")) minimum = 4096;
        }
        return Math.max(ordinary, anchored(minimum, diamond));
    }
    public long enchantPrice(String id, int level, long configured, long diamond) {
        if (!enabled) return configured;
        return Math.max(configured, anchored(enchants.getOrDefault(id, 4096L * Math.max(1, level)), diamond));
    }
    public void load(Path directory) {
        defaults();
        Path file = directory.resolve(FILE_NAME);
        try {
            if (Files.exists(file)) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Expected price version 1");
                boolean nextEnabled = root.get("enabled").getAsBoolean();
                Map<String, Long> nextItems = read(root.getAsJsonObject("minimumItemPrices"));
                Map<String, Long> nextEnchants = read(root.getAsJsonObject("minimumEnchantmentPrices"));
                items.putAll(nextItems); enchants.putAll(nextEnchants); enabled = nextEnabled;
            } else {
                JsonObject root = new JsonObject(); root.addProperty("version", 1); root.addProperty("enabled", true);
                root.addProperty("note", "Retail floors at diamond sell value 256; scale upward if diamonds are worth more. Set an individual floor to 0 to override it.");
                root.add("minimumItemPrices", new Gson().toJsonTree(items));
                root.add("minimumEnchantmentPrices", new Gson().toJsonTree(enchants));
                Files.createDirectories(directory);
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
            }
        } catch (IOException | RuntimeException ex) {
            ModLogger.error("Invalid retail price file; retaining safe acquisition premiums", ex);
        }
    }
    private static Map<String, Long> read(JsonObject object) {
        if (object == null || object.size() > 4096) throw new IllegalArgumentException("Missing or excessive retail prices");
        Map<String, Long> result = new LinkedHashMap<>();
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Retail price must be an integer: " + entry.getKey());
            long value = entry.getValue().getAsBigDecimal().longValueExact();
            if (!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value < 0 || value > MoneyMath.MAX_PRICE)
                throw new IllegalArgumentException("Invalid retail price " + entry.getKey());
            result.put(entry.getKey(), value);
        }
        return result;
    }
}
