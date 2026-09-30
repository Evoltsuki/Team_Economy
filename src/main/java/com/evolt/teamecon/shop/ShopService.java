package com.evolt.teamecon.shop;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.team.TeamUtil;
import com.evolt.teamecon.util.ModLogger;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.UUID;

/** Atomic item deliveries, configured mystery boxes and enchanted books. */
public final class ShopService {
    public enum Outcome { OK, NO_FUNDS, NO_SPACE, NO_POOL, STAGE_LOCKED, PROGRESSION_LOCKED, BAD_ITEM, NO_ENCHANT }
    public record Result(Outcome outcome, long charged, String detailKey, String detailArg, String rewards) {
        public Result(Outcome outcome, long charged, String detailKey, String detailArg) {
            this(outcome, charged, detailKey, detailArg, "");
        }
        public static Result ok(long charged) { return new Result(Outcome.OK, charged, "", ""); }
        public static Result fail(Outcome outcome) { return new Result(outcome, 0, "", ""); }
    }

    private final MinecraftServer server;
    private final TeamEconomyManager manager;
    private final PriceService prices;
    private final PurchaseRules progression;
    private final com.evolt.teamecon.gambling.CasinoProgression casino;
    private final com.evolt.teamecon.scratch.ScratchCardService cards;
    private final BlindBoxPools blindBoxes = new BlindBoxPools();
    private final EnchantShop enchants = new EnchantShop();

    public ShopService(MinecraftServer server, TeamEconomyManager manager, PriceService prices) {
        this(server, manager, prices, new PurchaseRules(server, manager));
    }

    public ShopService(MinecraftServer server, TeamEconomyManager manager, PriceService prices, PurchaseRules progression) {
        this(server, manager, prices, progression, new com.evolt.teamecon.gambling.CasinoProgression());
    }

    public ShopService(MinecraftServer server, TeamEconomyManager manager, PriceService prices, PurchaseRules progression,
                       com.evolt.teamecon.gambling.CasinoProgression casino) {
        this.server = server;
        this.manager = manager;
        this.prices = prices;
        this.progression = progression;
        this.casino = casino;
        this.cards = new com.evolt.teamecon.scratch.ScratchCardService(server, manager, casino);
    }

    public void loadConfigs(java.nio.file.Path configDir) {
        progression.load(configDir);
        blindBoxes.load(configDir, prices);
        enchants.load(configDir);
        enchants.priceOffers(offer -> prices.shopPrices().enchantPrice(offer.enchantmentId(), offer.level(),
                offer.price(), prices.basePrices().get("minecraft:diamond")));
        enchants.retain(offer -> {
            Holder<Enchantment> enchantment = enchantmentHolder(offer.enchantmentId());
            boolean valid = offer.enchantmentId().startsWith("minecraft:") && enchantment != null && offer.level() <= enchantment.value().getMaxLevel()
                    && offer.price() >= unitBuyPrice("minecraft:enchanted_book");
            if (!valid) ModLogger.warn("Rejected invalid or underpriced enchanted book: {}", offer.id());
            return valid;
        });
        ModLogger.info("Shop ready: {} boxes, {} enchanted books", blindBoxes.all().size(), enchants.all().size());
    }

    public BlindBoxPools blindBoxes() { return blindBoxes; }
    public EnchantShop enchants() { return enchants; }
    public PriceService prices() { return prices; }
    public PurchaseRules progression() { return progression; }
    public com.evolt.teamecon.scratch.ScratchCardService cards() { return cards; }
    public PurchaseRules.Access itemAccess(ServerPlayer player, String key) {
        if (!com.evolt.teamecon.price.TradePolicy.canTrade(key)) return PurchaseRules.Access.locked("mod_disabled");
        var access = casino.itemAccess(player, manager, key);
        return access.unlocked() ? progression.item(player, key) : access;
    }

    public Result upgradeLevel(ServerPlayer player, int expectedNextLevel) {
        UUID wallet = TeamUtil.walletKey(server, player.getUUID());
        if (!casino.valid()) return locked(PurchaseRules.Access.locked("casino_config"));
        if (expectedNextLevel != manager.casinoLevel(wallet) + 1 || expectedNextLevel > com.evolt.teamecon.gambling.CasinoProgression.MAX_LEVEL)
            return new Result(Outcome.BAD_ITEM, 0, "casino.teamecon.upgrade_changed", "");
        long cost = casino.levelCost(expectedNextLevel);
        if (!manager.canAfford(wallet, cost)) return new Result(Outcome.NO_FUNDS, cost, "", "");
        if (!manager.upgradeCasinoLevel(wallet, expectedNextLevel, cost))
            return new Result(Outcome.BAD_ITEM, 0, "casino.teamecon.upgrade_changed", "");
        manager.appendTransaction(new Transaction(manager.nextTxId(), wallet, player.getUUID(), player.getName().getString(),
                TxType.SERVICE, "casino_level:" + expectedNextLevel, 1, cost, -cost, System.currentTimeMillis()));
        return new Result(Outcome.OK, cost, "casino.teamecon.upgraded", String.valueOf(expectedNextLevel));
    }

    public Result buyTicket(ServerPlayer player, String id) { return buyTicket(player,id,1); }
    public Result buyTicket(ServerPlayer player, String id, int factor) {
        var kind = com.evolt.teamecon.scratch.ScratchKind.byId(id);
        if (kind == null) return Result.fail(Outcome.BAD_ITEM);
        var access = cards.access(player, kind);
        if (!access.unlocked()) return locked(access);
        String result = cards.buy(player, kind, factor);
        return new Result(result.equals("scratch.teamecon.purchased") ? Outcome.OK : Outcome.BAD_ITEM,
                MoneyMath.payout(kind.price(),factor), result, result.equals("command.teamecon.shop_no_funds") ? String.valueOf(MoneyMath.payout(kind.price(),factor)) : "");
    }

    public PurchaseRules.Access boxAccess(ServerPlayer player, ShopPool pool) {
        // Boxes have their own bounded prize pools and do not require advancements or stages.
        for (ShopPool.Entry entry : pool.entries()) {
            if (entry.itemKey().equals("minecraft:air")) continue;
            if (!BoxPrizePolicy.allowed(entry.itemKey())) return PurchaseRules.Access.locked("mod_disabled");
        }
        return PurchaseRules.Access.OPEN;
    }

    public PurchaseRules.Access enchantAccess(ServerPlayer player, EnchantShop.Offer offer) {
        if (!offer.enchantmentId().startsWith("minecraft:")) return PurchaseRules.Access.locked("mod_disabled");
        if (!unlocked(player, offer.stage())) return PurchaseRules.Access.locked("stage:" + offer.stage());
        return progression.enchantment(player, offer.enchantmentId());
    }

    private static Result locked(PurchaseRules.Access access) {
        return new Result(Outcome.PROGRESSION_LOCKED, 0, "shop.teamecon.progression_locked", access.reason());
    }

    public List<String> itemCatalog() {
        return prices.snapshot().keySet().stream().filter(key -> !key.equals("minecraft:enchanted_book"))
                .filter(key -> pricesItem(key) != null).sorted().toList();
    }

    public long unitBuyPrice(String key) {
        return Math.max(casino.itemPrice(key), prices.retailPrice(key, MoneyMath.buyPrice(prices.resolve(key).unitPrice(), TeConfig.SHOP.buyMarkup.get())));
    }

    public Item pricesItem(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || !prices.resolve(key).known()) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    public boolean unlocked(ServerPlayer player, String stage) {
        return stage == null || stage.isEmpty() || !TeConfig.SHOP.useTeamStages.get()
                || TeamUtil.hasStage(server, player.getUUID(), stage);
    }

    private void charge(ServerPlayer player, UUID wallet, long amount, TxType type, String detail, int count) {
        if (!manager.debit(wallet, amount)) throw new IllegalStateException("Unfunded purchase");
        manager.appendTransaction(new Transaction(manager.nextTxId(), wallet, player.getUUID(),
                player.getName().getString(), type, detail, count, amount / Math.max(1, count),
                -amount, System.currentTimeMillis()));
    }

    public Result buyItem(ServerPlayer player, Item item, int count) {
        if (item == null || item == Items.ENCHANTED_BOOK || count <= 0 || count > MoneyMath.MAX_PURCHASE)
            return Result.fail(Outcome.BAD_ITEM);
        String key = prices.itemKey(item);
        if (pricesItem(key) == null) return Result.fail(Outcome.BAD_ITEM);
        var access = itemAccess(player, key);
        if (!access.unlocked()) return locked(access);
        long total = MoneyMath.total(unitBuyPrice(key), count);
        if (total <= 0) return Result.fail(Outcome.BAD_ITEM);
        UUID wallet = TeamUtil.walletKey(server, player.getUUID());
        if (!manager.canAfford(wallet, total)) return new Result(Outcome.NO_FUNDS, total, "", "");
        ItemStack delivery = new ItemStack(item, count);
        if (!ItemDelivery.canFit(player.getInventory(), delivery)) return Result.fail(Outcome.NO_SPACE);
        charge(player, wallet, total, TxType.BUY, key, count);
        ItemDelivery.give(player.getInventory(), delivery);
        return new Result(Outcome.OK, total, "shop.teamecon.item_received", key + "," + count + "," + total);
    }

    public Result buyBlindBox(ServerPlayer player, String poolId) {
        return buyBlindBox(player, poolId, 1);
    }

    public Result buyBlindBox(ServerPlayer player, String poolId, int count) {
        if (count < 1 || count > 64) return Result.fail(Outcome.BAD_ITEM);
        ShopPool pool = blindBoxes.byId(poolId);
        if (pool == null || !pool.ready()) return Result.fail(Outcome.NO_POOL);
        var access = boxAccess(player, pool);
        if (!access.unlocked()) return locked(access);
        UUID wallet = TeamUtil.walletKey(server, player.getUUID());
        long total = MoneyMath.total(pool.price(), count);
        if (total <= 0) return Result.fail(Outcome.BAD_ITEM);
        if (!manager.canAfford(wallet, total)) return new Result(Outcome.NO_FUNDS, total, "", "");
        var outcomes = new java.util.ArrayList<ItemStack>();
        for (ShopPool.Entry entry : pool.entries()) {
            if (entry.itemKey().equals("minecraft:air")) continue;
            Item item = BoxPrizePolicy.item(entry.itemKey());
            if (item == null) return Result.fail(Outcome.BAD_ITEM);
            outcomes.add(new ItemStack(item, entry.count()));
        }
        if (!ItemDelivery.canFitAnyBatch(player.getInventory(), outcomes, count)) return Result.fail(Outcome.NO_SPACE);
        var prizes = new java.util.LinkedHashMap<String, Integer>();
        for (int i = 0; i < count; i++) {
            ShopPool.Entry drawn = pool.draw(player.getRandom());
            prizes.merge(drawn.itemKey(), drawn.itemKey().equals("minecraft:air") ? 1 : drawn.count(), Integer::sum);
        }
        charge(player, wallet, total, TxType.BLINDBOX, pool.id(), count);
        prizes.forEach((key, amount) -> {
            if (!key.equals("minecraft:air")) ItemDelivery.give(player.getInventory(), new ItemStack(BoxPrizePolicy.item(key), amount));
        });
        String receipt = prizes.entrySet().stream().map(e -> e.getKey() + "," + e.getValue()).collect(java.util.stream.Collectors.joining(";"));
        return new Result(Outcome.OK, total, "shop.teamecon.box_batch", count + "," + total, receipt);
    }

    /** Books use the vanilla stored-enchantment component, for normal anvil application. */
    public Result buyEnchant(ServerPlayer player, String offerId) {
        EnchantShop.Offer offer = enchants.byId(offerId);
        if (offer == null) return Result.fail(Outcome.NO_ENCHANT);
        var access = enchantAccess(player, offer);
        if (!access.unlocked()) return locked(access);
        Holder<Enchantment> enchantment = enchantmentHolder(offer.enchantmentId());
        if (enchantment == null || offer.level() > enchantment.value().getMaxLevel())
            return Result.fail(Outcome.NO_ENCHANT);
        UUID wallet = TeamUtil.walletKey(server, player.getUUID());
        if (!manager.canAfford(wallet, offer.price())) return new Result(Outcome.NO_FUNDS, offer.price(), "", "");
        ItemStack book = enchantedBook(enchantment, offer.level());
        if (!ItemDelivery.canFit(player.getInventory(), book)) return Result.fail(Outcome.NO_SPACE);
        charge(player, wallet, offer.price(), TxType.SERVICE, offer.enchantmentId(), 1);
        ItemDelivery.give(player.getInventory(), book);
        return new Result(Outcome.OK, offer.price(), "shop.teamecon.book_received",
                offer.enchantmentId() + "," + offer.level());
    }

    public static ItemStack enchantedBook(Holder<Enchantment> enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.upgrade(enchantment, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    private Holder<Enchantment> enchantmentHolder(String idText) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) return null;
        return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
    }
}
