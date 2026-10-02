package com.evolt.teamecon.price;

import com.evolt.teamecon.util.ModLogger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single entry point for "what is this worth". Resolution order:
 * 1. configured base price (raw materials),
 * 2. cheapest recipe-derived value (crafted goods, computed on demand by {@link RecipePricer}),
 * 3. rarity floor (loot and drops that cannot be crafted, valued by {@link RarityFallback}),
 * 4. unknown -> 0, and the item joins the pending list for later configuration.
 */
public final class PriceService {

    public enum Source {
        BASE, DERIVED, FALLBACK, UNKNOWN
    }

    public record Result(long unitPrice, Source source) {
        public boolean known() {
            return source != Source.UNKNOWN;
        }
    }

    private final BasePrices basePrices = new BasePrices();
    private final com.evolt.teamecon.shop.ShopCatalog catalog = new com.evolt.teamecon.shop.ShopCatalog();
    private final com.evolt.teamecon.shop.ShopPricing shopPrices = new com.evolt.teamecon.shop.ShopPricing();
    private final MaterialGroups groups = new MaterialGroups();
    private final Map<String, Long> derived = new HashMap<>();
    private final Map<String, Long> fallback = new ConcurrentHashMap<>();
    private final Set<String> unknown = ConcurrentHashMap.newKeySet();
    private volatile RarityFallback rarityFallback = RarityFallback.defaults();
    private volatile double craftValuePerStep = 0.1D;
    private volatile double maxCraftMarkup = 2.0D;
    private volatile boolean dirty = true;
    private HolderLookup.Provider provider;
    private RecipePricer pricer;

    public String itemKey(Item item) {
        return itemKeyStatic(item);
    }

    /** Static form for client-side helpers that must not hold a service instance. */
    public static String itemKeyStatic(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "minecraft:air" : id.toString();
    }

    public void setProvider(HolderLookup.Provider provider) {
        this.provider = provider;
    }

    HolderLookup.Provider provider() {
        return provider;
    }

    public BasePrices basePrices() {
        return basePrices;
    }
    public com.evolt.teamecon.shop.ShopCatalog catalog() { return catalog; }
    public boolean purchasable(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return catalog.allows(key) && id != null && BuiltInRegistries.ITEM.containsKey(id)
                && (catalog.custom(key) || resolve(key).known());
    }
    public long purchasePrice(String key, double markup) {
        long floor = com.evolt.teamecon.economy.MoneyMath.buyPrice(resolve(key).unitPrice(), markup);
        return catalog.price(key, retailPrice(key, floor), floor);
    }
    public com.evolt.teamecon.shop.ShopPricing shopPrices() { return shopPrices; }
    public long retailPrice(String key, long ordinary) {
        return shopPrices.itemPrice(key, ordinary, basePrices.get("minecraft:diamond"));
    }

    public MaterialGroups groups() {
        return groups;
    }

    public void setPricer(RecipePricer pricer) {
        this.pricer = pricer;
    }

    public RecipePricer pricer() {
        return pricer;
    }

    public void setRarityFallback(RarityFallback rarityFallback) {
        this.rarityFallback = rarityFallback == null ? RarityFallback.defaults() : rarityFallback;
        fallback.clear();
        markDirty();
    }

    /** Crafting adds a step value over ingredient cost; injected so the math stays testable. */
    public void setCraftValue(double step, double cap) {
        this.craftValuePerStep = step;
        this.maxCraftMarkup = cap;
        markDirty();
    }

    public double craftValuePerStep() {
        return craftValuePerStep;
    }

    public double maxCraftMarkup() {
        return maxCraftMarkup;
    }

    public RarityFallback rarityFallback() {
        return rarityFallback;
    }

    void clearDerived() {
        derived.clear();
        fallback.clear();
        unknown.clear();
        markDirty();
    }

    /** Keeps the lowest derived value seen, so roundabout recipes never raise the price. */
    void offerDerived(String itemKey, long value) {
        if (value <= 0) {
            return;
        }
        Long previous = derived.get(itemKey);
        if (previous == null || value < previous) {
            derived.put(itemKey, value);
            markDirty();
        }
    }

    /** Cached rarity floor for one item; 0 when it has none. Package-private so the pricer shares it. */
    long fallbackValue(String itemKey) {
        Long cached = fallback.get(itemKey);
        if (cached != null) {
            return cached;
        }
        long value = computeFallback(itemKey);
        fallback.put(itemKey, value);
        return value;
    }

    private long computeFallback(String itemKey) {
        ResourceLocation id = ResourceLocation.tryParse(itemKey);
        if (id == null || !TradePolicy.canTrade(itemKey) || !BuiltInRegistries.ITEM.containsKey(id)) {
            return 0L;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return 0L;
        }
        return rarityFallback.priceOf(new ItemStack(item).getRarity());
    }

    /** Marks the price table as changed so clients get a fresh snapshot. */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    /** Base + recipe-derived + rarity-floor prices; demand decay is applied per sale, so it is not included. */
    public java.util.Map<String, Long> snapshot() {
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        fallback.forEach((key, value) -> {
            if (value > 0) {
                out.put(key, value);
            }
        });
        out.putAll(derived);
        for (String key : groups.storageBlocks()) {
            Long value = groups.storageValue(key, basePrices);
            if (value != null) out.put(key, value);
        }
        out.putAll(basePrices.all());
        out.entrySet().removeIf(entry -> entry.getValue() <= 0 || !TradePolicy.canTrade(entry.getKey()));
        return out;
    }

    Long derivedValue(String itemKey) {
        return derived.get(itemKey);
    }

    int derivedCount() {
        return derived.size();
    }

    public Result resolve(String itemKey) {
        if (!TradePolicy.canTrade(itemKey)) return new Result(0L, Source.UNKNOWN);
        if (basePrices.has(itemKey)) {
            long value = basePrices.get(itemKey);
            return new Result(value, value > 0 ? Source.BASE : Source.UNKNOWN);
        }
        Long storage = groups.storageValue(itemKey, basePrices);
        if (storage != null) return new Result(storage, storage > 0 ? Source.DERIVED : Source.UNKNOWN);
        Long value = derived.get(itemKey);
        if (value != null) {
            return new Result(value, Source.DERIVED);
        }
        // Only items that actually have a recipe go through the pricer, so the floor never
        // overrides what a recipe is worth (a golden apple stays worth its gold content).
        if (pricer != null && pricer.hasRecipe(itemKey)) {
            long computed = pricer.computeUnit(itemKey, new java.util.HashSet<>());
            if (computed > 0) {
                return new Result(computed, Source.DERIVED);
            }
        }
        long floor = fallbackValue(itemKey);
        if (floor > 0) {
            return new Result(floor, Source.FALLBACK);
        }
        unknown.add(itemKey);
        return new Result(0L, Source.UNKNOWN);
    }

    /** Resolves every registered item so the rarity floor and the pending list are complete for clients. */
    public void collectFallbacks() {
        BuiltInRegistries.ITEM.forEach(item -> resolve(itemKey(item)));
        ModLogger.info("Price table: {} base, {} recipe-derived, {} rarity-priced, {} unresolved",
                basePrices.all().size(), derived.size(),
                fallback.values().stream().filter(value -> value > 0).count(),
                unknown.size());
    }

    public Set<String> unknownItems() {
        return unknown;
    }

    /** Demand-pool key and canonical units for market decay. */
    public String demandGroup(String itemKey) {
        return groups.groupFor(itemKey);
    }

    public double demandUnits(String itemKey, int count) {
        return groups.unitsFor(itemKey, count);
    }
}
