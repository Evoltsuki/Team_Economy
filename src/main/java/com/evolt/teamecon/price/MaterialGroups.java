package com.evolt.teamecon.price;

import java.util.HashMap;
import java.util.Map;

/**
 * Reversible material conversions (9 ingots <-> 1 block, 9 nuggets <-> 1 ingot, ...).
 * <p>
 * Items in the same group share one demand pool measured in canonical units, so crafting
 * and uncrafting cannot move a item between demand pools and reset the market dip.
 * Values are expressed in canonical units: 1 block of iron = 9 iron units.
 */
public final class MaterialGroups {

    public record GroupEntry(String group, double unitsPerItem) {
    }

    private final Map<String, GroupEntry> entries = new HashMap<>();

    public MaterialGroups() {
        defaults();
    }

    /** Canonical units contributed by one stack item, 1.0 for anything ungrouped. */
    public double unitsFor(String itemKey, int count) {
        GroupEntry entry = entries.get(itemKey);
        double per = entry == null ? 1D : entry.unitsPerItem;
        return per * count;
    }

    /** Demand-pool key for an item; the item itself when it has no conversion group. */
    public String groupFor(String itemKey) {
        GroupEntry entry = entries.get(itemKey);
        return entry == null ? itemKey : entry.group;
    }

    private void put(String group, String item, double units) {
        entries.put(item, new GroupEntry(group, units));
    }

    private void blockFamily(String name, String ingot) {
        // nugget = 1/9, ingot = 1, block = 9, all in the same canonical pool.
        put(ingot, ingot, 1D);
        if (name.equals("iron") || name.equals("gold"))
            put(ingot, "minecraft:" + name + "_nugget", 1D / 9D);
        put(ingot, "minecraft:" + name + "_block", 9D);
    }

    private void gemFamily(String item) {
        put(item, item, 1D);
        put(item, item + "_block", 9D);
    }

    private void defaults() {
        blockFamily("iron", "minecraft:iron_ingot");
        blockFamily("gold", "minecraft:gold_ingot");
        blockFamily("copper", "minecraft:copper_ingot");
        blockFamily("netherite", "minecraft:netherite_ingot");
        gemFamily("minecraft:diamond");
        gemFamily("minecraft:emerald");
        put("minecraft:lapis_lazuli", "minecraft:lapis_lazuli", 1D);
        put("minecraft:lapis_lazuli", "minecraft:lapis_block", 9D);
        gemFamily("minecraft:redstone");
        gemFamily("minecraft:coal");
        put("minecraft:quartz", "minecraft:quartz_block", 4D);
        put("minecraft:amethyst_shard", "minecraft:amethyst_block", 4D);
        put("minecraft:wheat", "minecraft:wheat", 1D);
        put("minecraft:wheat", "minecraft:hay_block", 9D);
        put("minecraft:wheat", "minecraft:bread", 3D); // approximates the flour-less vanilla recipe
        put("minecraft:melon_slice", "minecraft:melon_slice", 1D);
        put("minecraft:melon_slice", "minecraft:melon", 9D);
        put("minecraft:snowball", "minecraft:snowball", 1D);
        put("minecraft:snowball", "minecraft:snow_block", 4D);
    }
}
