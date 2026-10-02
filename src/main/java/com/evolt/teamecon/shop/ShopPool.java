package com.evolt.teamecon.shop;

import com.evolt.teamecon.util.ModLogger;
import com.evolt.teamecon.config.ConfigJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mystery-box prize pool, loaded from JSON so the odds stay public and server-configurable.
 * Each entry is an item stack with a weight; the expected value of a box is the
 * probability-weighted sum of stack sell prices, and it must stay under the box price or the
 * pool is rejected. That keeps blind boxes a priced convenience, never a money printer.
 */
public final class ShopPool {

    public record Entry(String itemKey, int count, int weight) {
    }

    private String id = "";
    private long price = 0L;
    private String stage = "";
    private boolean enabled = true, allowModdedItems, enforceValueCap = true;
    private final List<Entry> entries = new ArrayList<>();

    public String id() {
        return id;
    }

    public long price() {
        return price;
    }

    /** FTB Teams stage required to buy this box; empty means open to everyone. */
    public String stage() {
        return stage;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Test-only helper: adds an entry straight onto the pool. */
    void addEntry(String itemKey, int count, int weight) {
        entries.add(new Entry(itemKey, count, weight));
    }

    /** Test-only helper: sets the header fields without JSON. */
    void describe(String poolId, long poolPrice, String poolStage) {
        this.id = poolId;
        this.price = poolPrice;
        this.stage = poolStage;
    }

    public boolean ready() {
        return !entries.isEmpty() && price > 0 && totalWeight() > 0;
    }

    /** Total weight, used to turn weights into probabilities. */
    public int totalWeight() {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        return total;
    }

    /** Draws one entry using the supplied random source. */
    public Entry draw(net.minecraft.util.RandomSource random) {
        int total = totalWeight();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        int accumulated = 0;
        for (Entry entry : entries) {
            accumulated += entry.weight();
            if (roll < accumulated) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    public boolean enabled() { return enabled; }
    public boolean enforceValueCap() { return enforceValueCap; }
    public boolean allows(String key) {
        return BoxPrizePolicy.allowed(key) || allowModdedItems && ShopCatalog.id(key)
                && !key.startsWith("minecraft:") && !key.startsWith("teamecon:");
    }
    public net.minecraft.world.item.Item item(String key) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(key);
        return allows(key) && id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) : null;
    }

    /** Reject an entire malformed pool; skipping a reward would change the configured odds. */
    boolean read(JsonObject obj) {
        entries.clear();
        id = ConfigJson.text(obj, "id", "");
        enabled = ConfigJson.bool(obj, "enabled", true);
        allowModdedItems = ConfigJson.bool(obj, "allowModdedItems", false);
        enforceValueCap = ConfigJson.bool(obj, "enforceValueCap", true);
        price = ConfigJson.integer(obj, "price", 0, 1, com.evolt.teamecon.economy.MoneyMath.MAX_PRICE);
        stage = ConfigJson.text(obj, "stage", "");
        if (!id.matches("[a-z0-9_]{1,32}") || price <= 0 || stage.length() > 128
                || stage.contains(",") || stage.contains(";"))
            throw new IllegalArgumentException("Invalid blind box header: " + id);
        JsonArray array = obj.getAsJsonArray("entries");
        if (array == null || array.isEmpty() || array.size() > 128)
            throw new IllegalArgumentException("Pool " + id + " needs 1 to 128 entries");
        List<Entry> parsed = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject entry = array.get(i).getAsJsonObject();
            String key = ConfigJson.text(entry, "item", "");
            if (!ShopCatalog.id(key)) throw new IllegalArgumentException("Invalid item in " + id + " entry " + i);
            int count = (int) ConfigJson.integer(entry, "count", 1, 1, com.evolt.teamecon.economy.MoneyMath.MAX_PURCHASE);
            int weight = (int) ConfigJson.integer(entry, "weight", 1, 1, 1_000_000);
            parsed.add(new Entry(key, count, weight));
        }
        entries.addAll(parsed);
        return ready();
    }

    public static ShopPool fromFile(Path file) {
        ShopPool pool = new ShopPool();
        if (!Files.exists(file)) {
            return pool;
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element instanceof JsonObject obj) {
                pool.read(obj);
            }
        } catch (IOException | RuntimeException e) {
            ModLogger.error("Failed to read shop pool {}", file, e);
        }
        return pool;
    }
}
