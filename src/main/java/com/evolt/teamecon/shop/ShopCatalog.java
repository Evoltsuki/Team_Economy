package com.evolt.teamecon.shop;

import com.evolt.teamecon.config.ConfigJson;
import com.evolt.teamecon.economy.MoneyMath;
import com.evolt.teamecon.price.TradePolicy;
import com.evolt.teamecon.util.ModLogger;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/** A server-owned purchase catalogue, independent of the recycling price table. */
public final class ShopCatalog {
    public static final String FILE_NAME = "teamecon_shop_catalog.json";
    public record Offer(long price, String stage) {}
    private Map<String, Offer> offers = Map.of();
    private Set<String> disabled = Set.of();
    private boolean includeDefaults = true, valid = true;

    public boolean valid() { return valid; }
    public Set<String> customItems() { return offers.keySet(); }
    public boolean custom(String id) { return valid && offers.containsKey(id) && !disabled.contains(id); }
    public boolean allows(String id) {
        return valid && !disabled.contains(id) && (offers.containsKey(id) || includeDefaults && TradePolicy.canTrade(id));
    }
    public String stage(String id) { return custom(id) ? offers.get(id).stage() : ""; }
    public long price(String id, long ordinary, long resaleFloor) {
        return custom(id) ? Math.max(offers.get(id).price(), resaleFloor) : ordinary;
    }

    public void load(Path directory) {
        load(directory, id -> {
            ResourceLocation key = ResourceLocation.tryParse(id);
            return key != null && BuiltInRegistries.ITEM.containsKey(key);
        });
    }

    void load(Path directory, Predicate<String> registered) {
        Path file = directory.resolve(FILE_NAME);
        offers = Map.of(); disabled = Set.of(); includeDefaults = true; valid = true;
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(directory);
                Files.writeString(file, """
                        {
                          "version": 1,
                          "_comment": "Set includeDefaultItems=false for a custom-only shop. items entries: item, price, optional stage. Disabled IDs override items. JSON changes: /teamecon admin reload. See docs/server-configuration.md.",
                          "includeDefaultItems": true,
                          "disabledItems": [],
                          "items": []
                        }
                        """);
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (ConfigJson.integer(root, "version", -1, 1, 1) != 1)
                throw new IllegalArgumentException("Expected catalogue version 1");
            boolean nextDefaults = ConfigJson.bool(root, "includeDefaultItems", true);
            Set<String> nextDisabled = new LinkedHashSet<>();
            JsonArray exclusions = root.getAsJsonArray("disabledItems");
            if (exclusions != null) {
                if (exclusions.size() > 4096) throw new IllegalArgumentException("Too many disabled items");
                for (JsonElement value : exclusions) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !id(value.getAsString()))
                        throw new IllegalArgumentException("Invalid disabled item ID");
                    nextDisabled.add(value.getAsString());
                }
            }
            Map<String, Offer> next = new LinkedHashMap<>();
            JsonArray entries = root.getAsJsonArray("items");
            if (entries == null || entries.size() > 4096) throw new IllegalArgumentException("Expected items array (up to 4096)");
            for (JsonElement value : entries) {
                JsonObject entry = value.getAsJsonObject();
                String item = ConfigJson.text(entry, "item", "");
                if (!id(item) || !configurable(item)) throw new IllegalArgumentException("Unsupported shop item: " + item);
                if (next.containsKey(item)) throw new IllegalArgumentException("Duplicate shop item: " + item);
                long price = ConfigJson.integer(entry, "price", 0, 1, MoneyMath.MAX_PRICE);
                String stage = ConfigJson.text(entry, "stage", "");
                if (price == 0 || stage.length() > 128 || stage.contains(",") || stage.contains(";"))
                    throw new IllegalArgumentException("Invalid price/stage for " + item);
                // Keep the ID for duplicate detection, but a missing optional mod never becomes a purchasable item.
                next.put(item, new Offer(price, stage));
                if (!registered.test(item)) ModLogger.warn("Shop catalogue item is not installed: {}", item);
            }
            offers = Collections.unmodifiableMap(next);
            disabled = Set.copyOf(nextDisabled); includeDefaults = nextDefaults;
        } catch (java.io.IOException | RuntimeException ex) {
            valid = false;
            ModLogger.error("Invalid {}: item purchases disabled until repaired and reloaded", file, ex);
        }
    }

    public static boolean id(String value) { return value.length() <= 256 && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }
    public static boolean configurable(String id) {
        return TradePolicy.canTrade(id) || id(id) && !id.startsWith("minecraft:") && !id.startsWith("teamecon:");
    }
}
