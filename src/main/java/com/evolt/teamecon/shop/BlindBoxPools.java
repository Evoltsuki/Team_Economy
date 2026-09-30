package com.evolt.teamecon.shop;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.util.ModLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All mystery-box pools, loaded from the config folder. Each pool is validated on load:
 * the expected sell value of its rewards may not exceed the box price times the expected-value
 * cap, so a badly edited file can never turn boxes into profit.
 */
public final class BlindBoxPools {

    private static final String FILE_NAME = "teamecon_blindbox.json";

    private final Map<String, ShopPool> pools = new LinkedHashMap<>();

    public Collection<ShopPool> all() {
        return pools.values();
    }

    public ShopPool byId(String id) {
        return pools.get(id);
    }

    public boolean ready() {
        return !pools.isEmpty();
    }

    public void load(Path configDir, PriceService prices) {
        Path file = configDir.resolve(FILE_NAME);
        if (Files.exists(file)) {
            pools.clear();
            com.google.gson.JsonElement element;
            try (java.io.BufferedReader reader = Files.newBufferedReader(file)) {
                element = com.google.gson.JsonParser.parseReader(reader);
            } catch (IOException | RuntimeException e) {
                ModLogger.error("Failed to read blind box file; pools disabled", e);
                pools.clear();
                return;
            }
            if (element instanceof JsonArray array) {
                // Only replace the exact shipped legacy pools. Edited pools remain the owner's choice.
                if (array.equals(legacyDefaults()) || array.equals(previousDefaults())) {
                    try {
                        Path backup = file.resolveSibling(FILE_NAME + (array.equals(legacyDefaults()) ? ".pre-1.0.bak" : ".pre-expanded.bak"));
                        if (!Files.exists(backup)) Files.copy(file, backup);
                        defaults();
                        writeDefaults(file);
                        validate(prices);
                        return;
                    } catch (IOException e) {
                        ModLogger.error("Could not back up legacy boxes; keeping existing pools", e);
                    }
                }
                for (com.google.gson.JsonElement item : array) {
                    if (!(item instanceof JsonObject obj)) {
                        continue;
                    }
                    ShopPool pool = new ShopPool();
                    try {
                        if (pool.read(obj) && pools.size() < 64) pools.put(pool.id(), pool);
                    } catch (RuntimeException ex) {
                        ModLogger.warn("Ignored invalid blind box: {}", ex.toString());
                    }
                }
            }
        } else {
            defaults();
            writeDefaults(file);
        }
        validate(prices);
    }

    /** Nonempty supply boxes. Ordinary prizes cover the default retail cost, not the resale cost. */
    private void defaults() {
        pools.clear();
        ShopPool common = new ShopPool();
        common.read(json("common", 64L, "",
                "minecraft:iron_ingot|4|2200", "minecraft:coal|16|1500", "minecraft:oak_log|16|1500",
                "minecraft:copper_ingot|8|1000", "minecraft:lapis_lazuli|8|1000", "minecraft:quartz|16|1000",
                "minecraft:amethyst_shard|4|800", "minecraft:gold_ingot|2|500", "minecraft:emerald|2|300",
                "minecraft:diamond|2|180", "minecraft:netherite_ingot|1|20"));
        List<String> rewards = new ArrayList<>(List.of(
                "minecraft:diamond|1|2200", "minecraft:gold_ingot|4|1600", "minecraft:iron_ingot|32|1200",
                "minecraft:ender_pearl|2|1000", "minecraft:emerald|4|1000", "minecraft:amethyst_shard|32|800",
                "minecraft:diamond|4|600", "minecraft:netherite_scrap|2|400", "minecraft:netherite_ingot|1|100",
                "minecraft:diamond|16|50", "minecraft:enchanted_golden_apple|1|50"));
        // Every friendly/passive or non-hostile animal egg has an equal share of this 10% tier.
        int count = BoxPrizePolicy.EGGS.size(), index = 0;
        for (String egg : BoxPrizePolicy.EGGS.stream().sorted().toList())
            rewards.add(egg + "|1|" + (1000 / count + (index++ < 1000 % count ? 1 : 0)));
        ShopPool rare = new ShopPool();
        rare.read(json("rare", 512L, "", rewards.toArray(String[]::new)));
        pools.put(common.id(), common);
        pools.put(rare.id(), rare);
    }

    static JsonArray previousDefaults() {
        BlindBoxPools old = new BlindBoxPools();
        old.oldDefaults();
        JsonArray result = new JsonArray();
        for (ShopPool pool : old.all()) result.add(json(pool.id(), pool.price(), pool.stage(),
                pool.entries().stream().map(e -> e.itemKey()+"|"+e.count()+"|"+e.weight()).toArray(String[]::new)));
        return result;
    }

    private void oldDefaults() {
        pools.clear();
        ShopPool common = new ShopPool();
        common.read(json("common", 64L, "",
                "minecraft:iron_ingot|4|4500", "minecraft:coal|16|2500",
                "minecraft:oak_log|16|2000", "minecraft:copper_ingot|8|800", "minecraft:gold_ingot|1|200"));
        ShopPool rare = new ShopPool();
        rare.read(json("rare", 512L, "",
                "minecraft:diamond|1|4500", "minecraft:gold_ingot|4|2500",
                "minecraft:iron_ingot|32|1800", "minecraft:ender_pearl|2|1000",
                "minecraft:diamond|2|198", "minecraft:cow_spawn_egg|1|1", "minecraft:sheep_spawn_egg|1|1"));
        pools.put(common.id(), common);
        pools.put(rare.id(), rare);
    }

    static JsonArray legacyDefaults() {
        JsonArray array = new JsonArray();
        array.add(json("common", 64L, "", "minecraft:iron_ingot|4|40", "minecraft:coal|8|30",
                "minecraft:oak_log|8|20", "minecraft:rotten_flesh|4|10"));
        array.add(json("rare", 512L, "", "minecraft:gold_ingot|2|35", "minecraft:diamond|1|20",
                "minecraft:ender_pearl|2|20", "minecraft:iron_ingot|8|15", "minecraft:air|1|10"));
        return array;
    }

    private static JsonObject json(String id, long price, String stage, String... entries) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("price", price);
        obj.addProperty("stage", stage);
        JsonArray arr = new JsonArray();
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            JsonObject e = new JsonObject();
            e.addProperty("item", parts[0]);
            e.addProperty("count", Integer.parseInt(parts[1]));
            e.addProperty("weight", Integer.parseInt(parts[2]));
            arr.add(e);
        }
        obj.add("entries", arr);
        return obj;
    }

    private void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonArray array = new JsonArray();
            for (ShopPool pool : pools.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", pool.id());
                obj.addProperty("price", pool.price());
                obj.addProperty("stage", pool.stage());
                JsonArray entries = new JsonArray();
                for (ShopPool.Entry entry : pool.entries()) {
                    JsonObject e = new JsonObject();
                    e.addProperty("item", entry.itemKey());
                    e.addProperty("count", entry.count());
                    e.addProperty("weight", entry.weight());
                    entries.add(e);
                }
                obj.add("entries", entries);
                array.add(obj);
            }
            Files.writeString(file, com.google.gson.GsonBuilder.class.getName().isEmpty()
                    ? array.toString() : new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(array));
            ModLogger.info("Wrote default blind box file to {}", file);
        } catch (IOException e) {
            ModLogger.error("Could not write default blind box file", e);
        }
    }

    /** Expected value check: weighted sell value must stay at or under price times the cap. */
    void validate(PriceService prices) {
        if (prices == null) {
            return;
        }
        double cap = TeConfig.GAMBLING.expectedValueCap.get();
        List<ShopPool> safe = new ArrayList<>();
        for (ShopPool pool : pools.values()) {
            boolean valid = pool.ready() && pool.entries().stream().allMatch(entry -> {
                if (entry.itemKey().equals("minecraft:air")) return true;
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(entry.itemKey());
                return BoxPrizePolicy.allowed(entry.itemKey()) && id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                        && (BoxPrizePolicy.exclusive(entry.itemKey()) || prices.resolve(entry.itemKey()).known());
            });
            if (!valid) continue;
            double expected = expectedValue(pool, prices);
            if (expected > pool.price() * cap + 1e-9) {
                ModLogger.warn("Blind box '{}' rejected: expected value {} exceeds price {} x cap {}",
                        pool.id(), expected, pool.price(), cap);
                continue;
            }
            safe.add(pool);
        }
        pools.clear();
        for (ShopPool pool : safe) {
            pools.put(pool.id(), pool);
        }
        ModLogger.info("Blind boxes loaded: {} pools, cap {}", safe.size(), cap);
    }

    /** Probability-weighted sell value of one box, using the auto-valued unit prices. */
    public static double expectedValue(ShopPool pool, PriceService prices) {
        int total = pool.totalWeight();
        if (total <= 0) {
            return 0D;
        }
        double expected = 0D;
        for (ShopPool.Entry entry : pool.entries()) {
            if (BoxPrizePolicy.exclusive(entry.itemKey())) continue;
            PriceService.Result valued = prices.resolve(entry.itemKey());
            if (!valued.known()) {
                continue;
            }
            expected += ((double) entry.weight() / total) * valued.unitPrice() * entry.count();
        }
        return expected;
    }
}
