package com.evolt.teamecon.shop;

import com.evolt.teamecon.price.TradePolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.util.Set;

/** Prize-only livestock eggs do not become shop goods or recyclable currency. */
public final class BoxPrizePolicy {
    public static final Set<String> EGGS = java.util.Arrays.stream(new String[]{
            "allay", "armadillo", "axolotl", "bat", "bee", "camel", "cat", "chicken", "cod",
            "cow", "dolphin", "donkey", "fox", "frog", "glow_squid", "goat", "horse", "llama",
            "mooshroom", "mule", "ocelot", "panda", "parrot", "pig", "polar_bear", "pufferfish",
            "rabbit", "salmon", "sheep", "skeleton_horse", "sniffer", "snow_golem", "squid",
            "strider", "tadpole", "trader_llama", "tropical_fish", "turtle", "villager",
            "wandering_trader", "wolf", "iron_golem", "zombie_horse"
    }).map(name -> "minecraft:" + name + "_spawn_egg").collect(java.util.stream.Collectors.toUnmodifiableSet());
    private BoxPrizePolicy() {}
    public static boolean exclusive(String id) { return EGGS.contains(id); }
    public static boolean allowed(String id) {
        // Administrators may explicitly add hostile eggs; defaults still use EGGS.
        return id.startsWith("minecraft:") && id.endsWith("_spawn_egg") || TradePolicy.canSell(id);
    }
    public static Item item(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        return allowed(text) && id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.get(id) : null;
    }
}
