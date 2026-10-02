package com.evolt.teamecon.economy;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.market.DemandState;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.price.TradePolicy;
import com.evolt.teamecon.team.TeamUtil;
import com.evolt.teamecon.shop.PurchaseRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Sell and buy operations. Selling pays the auto-valued price times the market multiplier
 * and saturates demand; buying charges the same value times the shop markup. All of it is
 * recorded in the ledger with the player who acted.
 */
public final class EconomyService {

    public enum Outcome {
        OK, NOT_PRICED, NO_FUNDS, NO_SPACE, BALANCE_FULL, PROGRESSION_LOCKED
    }

    public record SellResult(Outcome outcome, long total, long unitPrice, int soldCount) {
    }

    public record BuyResult(Outcome outcome, long total, long unitPrice, int boughtCount) {
    }

    private final MinecraftServer server;
    private final TeamEconomyManager manager;
    private final PriceService prices;
    private final PurchaseRules progression;
    private final com.evolt.teamecon.gambling.CasinoProgression casino;

    public EconomyService(MinecraftServer server, TeamEconomyManager manager, PriceService prices) {
        this(server, manager, prices, new PurchaseRules(server, manager));
    }

    public EconomyService(MinecraftServer server, TeamEconomyManager manager, PriceService prices, PurchaseRules progression) {
        this(server, manager, prices, progression, new com.evolt.teamecon.gambling.CasinoProgression());
    }

    public EconomyService(MinecraftServer server, TeamEconomyManager manager, PriceService prices, PurchaseRules progression,
                          com.evolt.teamecon.gambling.CasinoProgression casino) {
        this.server = server;
        this.manager = manager;
        this.prices = prices;
        this.progression = progression;
        this.casino = casino;
    }

    /** Sells the whole stack the player offers. Items without a price are left alone. */
    public long saleQuote(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return -1;
        String key = prices.itemKey(stack.getItem());
        if (!prices.canSell(stack)) return -1;
        var valued = prices.resolve(key);
        if (!valued.known()) return -1;
        UUID wallet = TeamUtil.walletKey(server, player.getUUID());
        double units = prices.demandUnits(key, 1);
        double integral = manager.demand().factorIntegral(manager.demand().scopeFor(wallet),
                prices.demandGroup(key), units * stack.getCount(), System.currentTimeMillis());
        return MoneyMath.settle(valued.unitPrice() * integral / units, manager.saleRemainder(wallet)).points();
    }

    public SellResult sell(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return new SellResult(Outcome.NOT_PRICED, 0, 0, 0);
        }
        String itemKey = prices.itemKey(stack.getItem());
        if (!prices.canSell(stack)) return new SellResult(Outcome.NOT_PRICED, 0, 0, 0);
        PriceService.Result valued = prices.resolve(itemKey);
        if (!valued.known()) {
            return new SellResult(Outcome.NOT_PRICED, 0, 0, 0);
        }

        UUID teamKey = TeamUtil.walletKey(server, player.getUUID());
        int count = stack.getCount();
        long now = System.currentTimeMillis();
        UUID scope = manager.demand().scopeFor(teamKey);
        String group = prices.demandGroup(itemKey);
        double unitsPerItem = prices.demandUnits(itemKey, 1);

        long unitPrice = valued.unitPrice();
        double factorSum = manager.demand().factorIntegral(scope, group, unitsPerItem * count, now);
        double rawValue = unitPrice * factorSum / unitsPerItem;
        if (rawValue <= 0) return new SellResult(Outcome.NOT_PRICED, 0, unitPrice, 0);
        MoneyMath.Settlement settlement = MoneyMath.settle(rawValue, manager.saleRemainder(teamKey));
        long total = settlement.points();
        if (!manager.canCredit(teamKey, total)) return new SellResult(Outcome.BALANCE_FULL, 0, unitPrice, 0);

        total = manager.credit(teamKey, total);
        manager.setSaleRemainder(teamKey, settlement.remainder());
        manager.demand().addDip(scope, group, unitsPerItem * count, now);
        manager.appendTransaction(new Transaction(
                manager.nextTxId(), teamKey, player.getUUID(), player.getName().getString(),
                TxType.SELL, itemKey, count, unitPrice, total, now));
        stack.setCount(0);
        return new SellResult(Outcome.OK, total, unitPrice, count);
    }

    private record SaleLine(int slot, String key, String group, int count, long unitPrice, double units,
                            long points, double remainder) {}

    private java.util.List<SaleLine> salePlan(ServerPlayer player, net.minecraft.world.Container container, long now) {
        UUID wallet = TeamUtil.walletKey(server, player.getUUID());
        UUID scope = manager.demand().scopeFor(wallet);
        var offsets = new java.util.HashMap<String, Double>();
        var plan = new java.util.ArrayList<SaleLine>();
        double remainder = manager.saleRemainder(wallet);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            String key = prices.itemKey(stack.getItem());
            if (!prices.canSell(stack)) continue;
            var valued = prices.resolve(key);
            if (!valued.known()) continue;
            String group = prices.demandGroup(key);
            double unitsPerItem = prices.demandUnits(key, 1), units = unitsPerItem * stack.getCount();
            double integral = manager.demand().factorIntegralAfter(scope, group, offsets.getOrDefault(group, 0D), units, now);
            double raw = valued.unitPrice() * integral / unitsPerItem;
            if (raw <= 0) continue;
            var settled = MoneyMath.settle(raw, remainder);
            remainder = settled.remainder();
            offsets.merge(group, units, Double::sum);
            plan.add(new SaleLine(i, key, group, stack.getCount(), valued.unitPrice(), units, settled.points(), remainder));
        }
        return plan;
    }

    public long saleQuote(ServerPlayer player, net.minecraft.world.Container container) {
        var plan = salePlan(player, container, System.currentTimeMillis());
        return plan.isEmpty() ? -1 : plan.stream().mapToLong(SaleLine::points).sum();
    }

    /** All eligible deposits settle together; unsupported items remain in their slots. */
    public SellResult sellBatch(ServerPlayer player, net.minecraft.world.Container container) {
        long now = System.currentTimeMillis();
        var plan = salePlan(player, container, now);
        if (plan.isEmpty()) return new SellResult(Outcome.NOT_PRICED, 0, 0, 0);
        UUID wallet = TeamUtil.walletKey(server, player.getUUID());
        long total = plan.stream().mapToLong(SaleLine::points).sum();
        if (!manager.canCredit(wallet, total)) return new SellResult(Outcome.BALANCE_FULL, 0, 0, 0);
        manager.credit(wallet, total);
        manager.setSaleRemainder(wallet, plan.getLast().remainder());
        for (SaleLine line : plan) {
            manager.demand().addDip(manager.demand().scopeFor(wallet), line.group(), line.units(), now);
            manager.appendTransaction(new Transaction(manager.nextTxId(), wallet, player.getUUID(), player.getName().getString(),
                    TxType.SELL, line.key(), line.count(), line.unitPrice(), line.points(), now));
            container.removeItemNoUpdate(line.slot());
        }
        container.setChanged();
        return new SellResult(Outcome.OK, total, 0, plan.stream().mapToInt(SaleLine::count).sum());
    }

    /** Buys {@code count} of an item at shop price, if the team can afford it. */
    public BuyResult buy(ServerPlayer player, Item item, int count) {
        if (item == null || item == net.minecraft.world.item.Items.ENCHANTED_BOOK || count <= 0 || count > MoneyMath.MAX_PURCHASE)
            return new BuyResult(Outcome.NOT_PRICED, 0, 0, 0);
        String itemKey = prices.itemKey(item);
        if (!prices.purchasable(itemKey)) {
            return new BuyResult(Outcome.NOT_PRICED, 0, 0, 0);
        }
        if (!purchaseAccess(player, itemKey).unlocked())
            return new BuyResult(Outcome.PROGRESSION_LOCKED, 0, 0, 0);

        UUID teamKey = TeamUtil.walletKey(server, player.getUUID());
        long unitPrice = unitBuyPrice(itemKey);
        long total = MoneyMath.total(unitPrice, count);
        if (total <= 0) return new BuyResult(Outcome.NOT_PRICED, 0, 0, 0);
        if (!manager.canAfford(teamKey, total)) {
            return new BuyResult(Outcome.NO_FUNDS, total, unitPrice, 0);
        }
        ItemStack delivery = new ItemStack(item, count);
        if (!ItemDelivery.canFit(player.getInventory(), delivery)) {
            return new BuyResult(Outcome.NO_SPACE, total, unitPrice, 0);
        }
        manager.debit(teamKey, total);
        ItemDelivery.give(player.getInventory(), delivery);
        manager.appendTransaction(new Transaction(
                manager.nextTxId(), teamKey, player.getUUID(), player.getName().getString(),
                TxType.BUY, itemKey, count, unitPrice, -total, System.currentTimeMillis()));
        return new BuyResult(Outcome.OK, total, unitPrice, count);
    }

    public PriceService prices() {
        return prices;
    }

    public long unitBuyPrice(String itemKey) {
        return Math.max(casino.itemPrice(itemKey), prices.purchasePrice(itemKey, TeConfig.SHOP.buyMarkup.get()));
    }

    public PurchaseRules.Access purchaseAccess(ServerPlayer player, String itemKey) {
        var access = casino.itemAccess(player, manager, itemKey);
        return access.unlocked() ? progression.item(player, itemKey, prices.catalog(), prices.overrides().get(itemKey).buy() > 0) : access;
    }

    public TeamEconomyManager manager() {
        return manager;
    }
}
