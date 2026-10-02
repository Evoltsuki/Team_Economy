package com.evolt.teamecon.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import java.util.*;

/** A bounded, portable reward description shared by configuration, previews and saved claims. */
public record BoxReward(String itemKey, String potion, int count) {
    public BoxReward {
        if (!ShopCatalog.id(itemKey) || !potion.isEmpty() && !ShopCatalog.id(potion)
                || count < 1 || count > 64 * 2304) throw new IllegalArgumentException("Invalid box reward");
        if (!potion.isEmpty() && !potionItem(itemKey)) throw new IllegalArgumentException("Potion data on a non-potion item");
    }
    public static boolean potionItem(String item) {
        return Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow").contains(item);
    }
    public boolean available() {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemKey)) && (potion.isEmpty()
                || BuiltInRegistries.POTION.containsKey(ResourceLocation.parse(potion)));
    }
    public ItemStack stack() {
        if (!available() || itemKey.equals("minecraft:air")) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemKey)), count);
        if (!potion.isEmpty()) stack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(BuiltInRegistries.POTION.getHolder(ResourceLocation.parse(potion)).orElseThrow()));
        return stack;
    }
    public BoxReward withCount(int amount) { return new BoxReward(itemKey, potion, amount); }
    /** Never silently strip a sample's enchantments, names, contents or other custom data. */
    public static BoxReward fromStack(ItemStack sample) {
        if (sample.isEmpty()) throw new IllegalArgumentException("Empty sample");
        String item = BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
        BoxReward reward = new BoxReward(item, potionItem(item) ? potionId(sample) : "", sample.getCount());
        if (!ItemStack.isSameItemSameComponents(sample, reward.stack()))
            throw new IllegalArgumentException("Unsupported sample data");
        return reward;
    }
    private static String potionId(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion()
                .map(p -> BuiltInRegistries.POTION.getKey(p.value()).toString()).orElse("");
    }
    public static List<BoxReward> merge(List<BoxReward> entries) {
        Map<String, BoxReward> merged = new LinkedHashMap<>();
        for (BoxReward e : entries) {
            String key = e.itemKey + "@" + e.potion;
            BoxReward old = merged.get(key);
            merged.put(key, old == null ? e : e.withCount(Math.addExact(old.count, e.count)));
        }
        return List.copyOf(merged.values());
    }
    public static String encode(List<BoxReward> rewards) {
        return rewards.stream().map(e -> e.itemKey + "," + e.count + "," + e.potion).collect(java.util.stream.Collectors.joining(";"));
    }
    public static List<BoxReward> decode(String text) {
        if (text.length() > 32767) throw new IllegalArgumentException("Reward list too large");
        List<BoxReward> rewards = new ArrayList<>();
        if (!text.isEmpty()) for (String row : text.split(";")) {
            String[] fields = row.split(",", -1);
            if (fields.length != 3 || rewards.size() >= 64) throw new IllegalArgumentException("Invalid reward list");
            rewards.add(new BoxReward(fields[0], fields[2], Integer.parseInt(fields[1])));
        }
        return List.copyOf(rewards);
    }
}
