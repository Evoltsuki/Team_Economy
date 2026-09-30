package com.evolt.teamecon.economy;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Check the entire delivery before charging or modifying any inventory slot. */
public final class ItemDelivery {
    private ItemDelivery() {}

    /** Worst-case space over every possible batch, before randomness or payment is consumed. */
    public static boolean canFitAnyBatch(Inventory inventory, java.util.List<ItemStack> outcomes, int count) {
        if (count < 1 || count > 64) return false;
        var distinct = new java.util.ArrayList<ItemStack>();
        for (ItemStack outcome : outcomes) {
            if (outcome.isEmpty()) continue;
            ItemStack same = distinct.stream().filter(s -> ItemStack.isSameItemSameComponents(s, outcome)).findFirst().orElse(null);
            if (same == null) distinct.add(outcome.copy());
            else same.setCount(Math.max(same.getCount(), outcome.getCount()));
        }
        int empty = (int) inventory.items.stream().filter(ItemStack::isEmpty).count();
        int[] worst = new int[count + 1];
        for (ItemStack outcome : distinct) {
            int free = inventory.items.stream().filter(s -> !s.isEmpty() && ItemStack.isSameItemSameComponents(s, outcome))
                    .mapToInt(s -> Math.max(0, s.getMaxStackSize() - s.getCount())).sum();
            int[] next = worst.clone();
            for (int used = 0; used <= count; used++) for (int n = 1; n + used <= count; n++) {
                int additional = Math.max(0, n * outcome.getCount() - free);
                int slots = (additional + outcome.getMaxStackSize() - 1) / outcome.getMaxStackSize();
                next[used + n] = Math.max(next[used + n], worst[used] + slots);
            }
            worst = next;
        }
        return worst[count] <= empty;
    }

    public static boolean canFit(Inventory inventory, ItemStack offered) {
        if (offered.isEmpty()) return true;
        int remaining = offered.getCount();
        for (ItemStack slot : inventory.items) {
            if (slot.isEmpty()) remaining -= offered.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(slot, offered))
                remaining -= Math.max(0, Math.min(slot.getMaxStackSize(), offered.getMaxStackSize()) - slot.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    public static void give(Inventory inventory, ItemStack offered) {
        if (offered.isEmpty()) return;
        if (!canFit(inventory, offered)) throw new IllegalStateException("Delivery was not preflighted");
        ItemStack remaining = offered.copy();
        for (ItemStack slot : inventory.items) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, remaining)) {
                int added = Math.min(remaining.getCount(), Math.max(0, slot.getMaxStackSize() - slot.getCount()));
                slot.grow(added);
                remaining.shrink(added);
            }
            if (remaining.isEmpty()) break;
        }
        for (int i = 0; i < inventory.items.size() && !remaining.isEmpty(); i++) {
            if (inventory.items.get(i).isEmpty()) {
                int added = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                inventory.items.set(i, remaining.copyWithCount(added));
                remaining.shrink(added);
            }
        }
        inventory.setChanged();
    }
}
