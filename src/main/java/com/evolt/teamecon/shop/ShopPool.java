package com.evolt.teamecon.shop;

import com.evolt.teamecon.util.ModLogger;
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

    /** Parses one pool from a JSON object; returns false when the entry list is unusable. */
    boolean read(JsonObject obj) {
        id = obj.has("id") ? obj.get("id").getAsString() : "";
        price = obj.has("price") ? obj.get("price").getAsLong() : 0L;
        stage = obj.has("stage") ? obj.get("stage").getAsString() : "";
        entries.clear();
        if (obj.has("entries") && obj.get("entries") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject entryObj)) {
                    continue;
                }
                String item = entryObj.has("item") ? entryObj.get("item").getAsString() : "";
                int count = entryObj.has("count") ? entryObj.get("count").getAsInt() : 1;
                int weight = entryObj.has("weight") ? entryObj.get("weight").getAsInt() : 1;
                if (item.isEmpty() || weight <= 0 || weight > 1_000_000 || count < 1
                        || count > com.evolt.teamecon.economy.MoneyMath.MAX_PURCHASE || entries.size() >= 128) {
                    ModLogger.warn("Shop pool '{}' skipped an entry without item or weight", id);
                    continue;
                }
                entries.add(new Entry(item, count, weight));
            }
        }
        return ready() && id.matches("[a-z0-9_]{1,32}")
                && price <= com.evolt.teamecon.economy.MoneyMath.MAX_PRICE
                && stage.length() <= 128 && !stage.contains(",") && !stage.contains(";");
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
