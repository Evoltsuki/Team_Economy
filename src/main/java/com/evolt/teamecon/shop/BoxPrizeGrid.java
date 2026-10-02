package com.evolt.teamecon.shop;

import com.evolt.teamecon.config.ConfigJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Prize templates have positions, but blank cells are not entries in the random draw. */
public final class BoxPrizeGrid {
    public static final int CAPACITY = 128;
    private final JsonObject[] cells = new JsonObject[CAPACITY];

    public BoxPrizeGrid(JsonArray entries) {
        if (entries.size() > CAPACITY) throw new IllegalArgumentException("Too many prizes");
        // Reserve explicit positions before placing older entries without layout metadata.
        for (JsonElement entry : entries) {
            JsonObject value = entry.getAsJsonObject();
            if (value.has("slot")) {
                int slot = (int) ConfigJson.integer(value, "slot", -1, 0, CAPACITY - 1);
                if (cells[slot] != null) throw new IllegalArgumentException("Duplicate prize slot");
                set(slot, value);
            }
        }
        for (JsonElement entry : entries) {
            JsonObject value = entry.getAsJsonObject();
            if (!value.has("slot")) set(firstEmpty(), value);
        }
    }
    public JsonObject get(int slot) { return cells[slot]; }
    public void set(int slot, JsonObject entry) { cells[slot] = entry == null ? null : entry.deepCopy(); }
    public int firstEmpty() {
        for (int i = 0; i < CAPACITY; i++) if (cells[i] == null) return i;
        return -1;
    }
    public void swap(int from, int to) {
        JsonObject entry = cells[from]; cells[from] = cells[to]; cells[to] = entry;
    }
    public JsonArray entries() {
        JsonArray result = new JsonArray();
        for (int i = 0; i < CAPACITY; i++) if (cells[i] != null) {
            JsonObject entry = cells[i].deepCopy(); entry.addProperty("slot", i); result.add(entry);
        }
        return result;
    }
}
