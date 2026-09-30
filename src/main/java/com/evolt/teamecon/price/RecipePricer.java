package com.evolt.teamecon.price;

import com.evolt.teamecon.util.ModLogger;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives prices for crafted goods from their cheapest standard recipe, on demand.
 * <p>
 * Only the recipe graph decides value: the least expensive valid route wins, so a player
 * deliberately crafting the long way round, or simply waiting, never raises a price.
 * Reversible conversions are pooled by {@link MaterialGroups} so recrafting cannot reset
 * demand. Cycles in the recipe graph are cut instead of followed.
 */
public final class RecipePricer {

    private final PriceService prices;
    private final Map<String, List<Recipe<?>>> index = new HashMap<>();
    private final Set<String> pending = new HashSet<>();

    public RecipePricer(PriceService prices) {
        this.prices = prices;
    }

    /** Indexes every crafting/cooking recipe by output. Call on server start and after datapack reloads. */
    public void indexRecipes(RecipeManager recipeManager, HolderLookup.Provider provider) {
        index.clear();
        pending.clear();
        prices.clearDerived();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe) && !(recipe instanceof AbstractCookingRecipe)) {
                continue;
            }
            indexRecipe(recipe, holder.id().toString(), provider);
        }
        ModLogger.info("Recipe index built over {} outputs", index.size());
    }

    private void indexRecipe(Recipe<?> recipe, String id, HolderLookup.Provider provider) {
        try {
            ItemStack result = recipe.getResultItem(provider);
            if (result.isEmpty() || result.getCount() <= 0) {
                return;
            }
            index.computeIfAbsent(prices.itemKey(result.getItem()), k -> new ArrayList<>()).add(recipe);
        } catch (Exception e) {
            ModLogger.warn("Skipped recipe {}: {}", id, e.toString());
        }
    }

    /** True when at least one indexed recipe produces this item. */
    boolean hasRecipe(String itemKey) {
        return index.containsKey(itemKey);
    }

    /** Unit price of one item, resolved recursively from base prices and the recipe graph. */
    public long computeUnit(String itemKey, Set<String> stack) {
        if (!TradePolicy.canTrade(itemKey)) return 0;
        if (prices.basePrices().has(itemKey)) {
            return prices.basePrices().get(itemKey);
        }
        Long cached = prices.derivedValue(itemKey);
        if (cached != null) {
            return cached;
        }
        if (!stack.add(itemKey)) {
            return 0;
        }
        try {
            List<Recipe<?>> recipes = index.getOrDefault(itemKey, Collections.emptyList());
            long best = Long.MAX_VALUE;
            for (Recipe<?> recipe : recipes) {
                long unit = priceRecipe(recipe, stack);
                if (unit > 0 && unit < best) {
                    best = unit;
                }
            }
            if (best == Long.MAX_VALUE) {
                // Nothing crafts this: it is loot or a drop. The rarity floor keeps it - and
                // every recipe that consumes it, golden apples included - priced instead of
                // collapsing the whole chain to zero.
                long floor = prices.fallbackValue(itemKey);
                if (floor > 0) {
                    return floor;
                }
                pending.add(itemKey);
                return 0;
            }
            prices.offerDerived(itemKey, best);
            return best;
        } finally {
            stack.remove(itemKey);
        }
    }

    private long priceRecipe(Recipe<?> recipe, Set<String> stack) {
        HolderLookup.Provider provider = prices.provider();
        if (provider == null) {
            return 0;
        }
        ItemStack result = recipe.getResultItem(provider);
        int outputCount = result.getCount();
        if (outputCount <= 0) {
            return 0;
        }
        long ingredientCost = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            long cheapest = Long.MAX_VALUE;
            int cheapestCount = 1;
            for (ItemStack choice : ingredient.getItems()) {
                if (choice.isEmpty()) {
                    continue;
                }
                long unit = computeUnit(prices.itemKey(choice.getItem()), stack);
                if (unit > 0 && unit < cheapest) {
                    cheapest = unit;
                    cheapestCount = choice.getCount();
                }
            }
            if (cheapest == Long.MAX_VALUE) {
                return 0;
            }
            ingredientCost += cheapest * cheapestCount;
        }
        if (ingredientCost <= 0) {
            return 0;
        }
        return applyCraftValue(ingredientCost / (double) outputCount);
    }

    private long applyCraftValue(double base) {
        double markup = 1D + Math.min(prices.craftValuePerStep(), prices.maxCraftMarkup());
        return Math.min(com.evolt.teamecon.economy.MoneyMath.MAX_PRICE, Math.max(1L, (long) Math.floor(base * markup)));
    }

    /** Prices every indexed output so the server can snapshot a complete table for clients. */
    public void computeAll() {
        for (String itemKey : index.keySet()) {
            computeUnit(itemKey, new HashSet<>());
        }
        ModLogger.info("Derived prices computed for {} items", prices.derivedCount());
    }

    public Set<String> pendingItems() {
        return pending;
    }

    public void forgetPending(Iterable<String> keys) {
        keys.forEach(pending::remove);
    }
}
