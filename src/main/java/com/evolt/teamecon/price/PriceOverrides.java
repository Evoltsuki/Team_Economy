package com.evolt.teamecon.price;

import com.evolt.teamecon.config.ConfigJson;
import com.evolt.teamecon.economy.MoneyMath;
import com.evolt.teamecon.shop.ShopCatalog;
import com.evolt.teamecon.util.ModLogger;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Explicit per-item trading overrides. These do not change recipe material anchors. */
public final class PriceOverrides {
    public static final String FILE_NAME = "teamecon_price_overrides.json";
    public static final Entry DEFAULT = new Entry(-1, -1);
    public record Entry(long buy, long sell) {
        public Entry {
            if (buy < -1 || sell < -1 || buy > MoneyMath.MAX_PRICE || sell > MoneyMath.MAX_PRICE)
                throw new IllegalArgumentException("Price outside supported range");
        }
    }
    private Map<String, Entry> entries = Map.of();
    private Path file;
    private String diskContents;
    private long revision;
    private boolean valid = true;

    public Entry get(String id) { return entries.getOrDefault(id, DEFAULT); }
    public Map<String, Entry> all() { return entries; }
    public long revision() { return revision; }
    public boolean valid() { return valid; }

    public static void validate(String id, Entry value) {
        if (!ShopCatalog.id(id) || !ShopCatalog.configurable(id) || id.equals("minecraft:enchanted_book") && value.buy() > 0)
            throw new IllegalArgumentException("Unsupported purchase item");
        if (id.startsWith("teamecon:") && value.sell() > 0)
            throw new IllegalArgumentException("Equipment cannot be recycled");
    }

    public void load(Path directory) {
        file = directory.resolve(FILE_NAME);
        entries = Map.of(); valid = false; revision++;
        try {
            diskContents = readDisk();
            if (diskContents == null) { valid = true; return; }
            JsonObject root = JsonParser.parseString(diskContents).getAsJsonObject();
            if (ConfigJson.integer(root, "version", -1, 1, 1) != 1)
                throw new IllegalArgumentException("Expected override version 1");
            JsonObject items = root.getAsJsonObject("items");
            if (items == null || items.size() > 4096) throw new IllegalArgumentException("Too many overrides");
            Map<String, Entry> next = new TreeMap<>();
            for (var row : items.entrySet()) {
                JsonObject obj = row.getValue().getAsJsonObject();
                Entry value = new Entry(ConfigJson.integer(obj, "buy", -1, -1, MoneyMath.MAX_PRICE),
                        ConfigJson.integer(obj, "sell", -1, -1, MoneyMath.MAX_PRICE));
                validate(row.getKey(), value);
                if (!value.equals(DEFAULT)) next.put(row.getKey(), value);
            }
            entries = Map.copyOf(next); valid = true;
        } catch (IOException | RuntimeException ex) {
            ModLogger.error("Invalid {}: trading disabled until repaired and reloaded", file, ex);
        }
    }

    private String readDisk() throws IOException {
        if (!Files.exists(file)) return null;
        if (Files.size(file) > 2_000_000) throw new IOException("Override file is too large");
        return Files.readString(file);
    }

    /** Optimistic revision and disk checks prevent silently overwriting another admin's changes. */
    public void save(String id, Entry value, long expectedRevision) throws IOException {
        validate(id, value);
        if (!valid || file == null) throw new IOException("Override configuration unavailable");
        if (revision != expectedRevision) throw new ConcurrentModificationException("Prices changed; select the item again");
        if (!Objects.equals(diskContents, readDisk())) throw new IOException("File changed externally; reload first");
        Map<String, Entry> next = new TreeMap<>(entries);
        if (value.equals(DEFAULT)) next.remove(id); else next.put(id, value);
        if (next.size() > 4096) throw new IllegalArgumentException("Too many overrides");
        JsonObject root = new JsonObject(), items = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("_comment", "Managed by /teamecon admin prices. Missing/-1: inherit, 0: disabled, positive: custom unit price. Recipe anchors stay in teamecon_base_prices.json.");
        for (var row : next.entrySet()) {
            JsonObject obj = new JsonObject();
            if (row.getValue().buy() != -1) obj.addProperty("buy", row.getValue().buy());
            if (row.getValue().sell() != -1) obj.addProperty("sell", row.getValue().sell());
            items.add(row.getKey(), obj);
        }
        root.add("items", items);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "teamecon-prices-", ".tmp");
        try {
            Files.writeString(temporary, json);
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(FILE_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            entries = Map.copyOf(next); diskContents = json; revision++;
        } finally { Files.deleteIfExists(temporary); }
    }
}
