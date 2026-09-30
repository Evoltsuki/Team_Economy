package com.evolt.teamecon.economy;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.gambling.BetSession;
import com.evolt.teamecon.gambling.SessionKey;
import com.evolt.teamecon.gambling.PendingMachineGame;
import com.evolt.teamecon.market.DemandState;
import com.evolt.teamecon.util.ModLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side store for everything economy: team balances, ledgers, aggregate stats,
 * quest-claim state and market demand. Attached to the overworld so a single save holds
 * the whole economy and survives world backup/restore.
 */
public class TeamEconomyManager extends SavedData {

    private static final String DATA_NAME = "teamecon_economy";

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Integer> casinoLevels = new HashMap<>();
    private final Set<UUID> progressionBypasses = new HashSet<>();
    private final Map<UUID, List<Transaction>> ledgers = new HashMap<>();
    private final Map<UUID, TeamStats> stats = new HashMap<>();
    private final Map<UUID, Set<String>> claimedQuests = new HashMap<>();
    private final Map<SessionKey, BetSession> bets = new HashMap<>();
    private final Map<SessionKey, PendingMachineGame> pendingMachines = new HashMap<>();
    private final Map<UUID, com.evolt.teamecon.scratch.ScratchTicket> scratchTickets = new HashMap<>();
    private final Map<UUID, Double> saleRemainders = new HashMap<>();
    private final DemandState demand = new DemandState(com.evolt.teamecon.market.MarketParams.fromConfig());
    private final AtomicLong txIdSeq = new AtomicLong(1);

    public static TeamEconomyManager get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TeamEconomyManager::new, TeamEconomyManager::load, null),
                DATA_NAME);
    }

    public TeamEconomyManager() {
    }

    public static TeamEconomyManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        TeamEconomyManager manager = new TeamEconomyManager();
        CompoundTag bypasses = tag.getCompound("progressionBypasses");
        for (String key : bypasses.getAllKeys()) {
            try {
                if (bypasses.getBoolean(key)) manager.progressionBypasses.add(UUID.fromString(key));
            } catch (IllegalArgumentException ex) { ModLogger.warn("Ignoring invalid progression bypass UUID: {}", key); }
        }
        CompoundTag levels = tag.getCompound("casinoLevels");
        for (String key : levels.getAllKeys()) {
            manager.casinoLevels.put(UUID.fromString(key), Math.clamp(levels.getInt(key), 1, 5));
        }
        CompoundTag balances = tag.getCompound("balances");
        for (String key : balances.getAllKeys()) {
            manager.balances.put(UUID.fromString(key), manager.clampBalance(balances.getLong(key)));
        }
        CompoundTag statsTag = tag.getCompound("stats");
        for (String key : statsTag.getAllKeys()) {
            manager.stats.put(UUID.fromString(key), TeamStats.load(statsTag.getCompound(key)));
        }
        CompoundTag ledgersTag = tag.getCompound("ledgers");
        for (String key : ledgersTag.getAllKeys()) {
            List<Transaction> list = new ArrayList<>();
            Transaction.readList(ledgersTag.getList(key, Tag.TAG_COMPOUND), list);
            manager.ledgers.put(UUID.fromString(key), list);
            for (Transaction tx : list) {
                manager.txIdSeq.set(Math.max(manager.txIdSeq.get(), tx.id() + 1));
            }
        }
        CompoundTag claims = tag.getCompound("claimedQuests");
        for (String playerKey : claims.getAllKeys()) {
            Set<String> ids = new HashSet<>();
            for (Tag t : claims.getList(playerKey, Tag.TAG_STRING)) {
                ids.add(t.getAsString());
            }
            manager.claimedQuests.put(UUID.fromString(playerKey), ids);
        }
        if (tag.contains("demand")) {
            manager.demand.loadInto(tag.getCompound("demand"));
        }
        manager.txIdSeq.set(Math.max(manager.txIdSeq.get(), tag.getLong("nextTxId")));
        CompoundTag betsTag = tag.getCompound("activeBets");
        CompoundTag remainderTag = tag.getCompound("saleRemainders");
        for (String key : remainderTag.getAllKeys()) {
            double value = remainderTag.getDouble(key);
            if (Double.isFinite(value) && value >= 0 && value < 1) manager.saleRemainders.put(UUID.fromString(key), value);
        }
        for (String key : betsTag.getAllKeys()) {
            try {
                CompoundTag run = betsTag.getCompound(key);
                BetSession session=BetSession.restore(run.getLong("stake"),
                        run.getUUID("wallet"), run.getString("game"), run.getDouble("maxMultiplier"),
                        run.getDouble("multiplier"), run.getInt("rounds"));
                if (run.contains("crashStarted")) session.restoreCrash(run.getLong("crashStarted"), run.getLong("crashDue"));
                if(run.contains("machinePos"))session.bindMachine(run.getString("machineDimension"),net.minecraft.core.BlockPos.of(run.getLong("machinePos")));
                UUID player = run.hasUUID("player") ? run.getUUID("player") : UUID.fromString(key);
                manager.bets.put(SessionKey.of(player, session),session);
            } catch (RuntimeException ex) {
                ModLogger.warn("Ignoring invalid saved betting session {}: {}", key, ex.toString());
            }
        }
        CompoundTag pending = tag.getCompound("pendingMachines");
        for (String key : pending.getAllKeys()) {
            try {
                PendingMachineGame game = PendingMachineGame.load(pending.getCompound(key));
                manager.pendingMachines.put(SessionKey.of(game), game);
            } catch (RuntimeException ex) { ModLogger.warn("Invalid saved machine action {}: {}", key, ex.toString()); }
        }
        CompoundTag tickets = tag.getCompound("scratchTickets");
        for (String key : tickets.getAllKeys()) {
            try { var ticket = com.evolt.teamecon.scratch.ScratchTicket.load(tickets.getCompound(key)); manager.scratchTickets.put(ticket.id(),ticket); }
            catch (RuntimeException ex) { ModLogger.warn("Invalid saved scratch ticket {}: {}", key, ex.toString()); }
        }
        ModLogger.info("TeamEconomy data loaded: {} wallets, {} ledgers", manager.balances.size(), manager.ledgers.size());
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag balancesTag = new CompoundTag();
        this.balances.forEach((k, v) -> balancesTag.putLong(k.toString(), v));
        tag.put("balances", balancesTag);
        CompoundTag levels = new CompoundTag();
        casinoLevels.forEach((wallet, level) -> levels.putInt(wallet.toString(), level));
        tag.put("casinoLevels", levels);
        CompoundTag bypasses = new CompoundTag();
        progressionBypasses.forEach(player -> bypasses.putBoolean(player.toString(), true));
        tag.put("progressionBypasses", bypasses);

        CompoundTag statsTag = new CompoundTag();
        stats.forEach((k, v) -> statsTag.put(k.toString(), v.save()));
        tag.put("stats", statsTag);

        CompoundTag ledgersTag = new CompoundTag();
        ledgers.forEach((k, v) -> ledgersTag.put(k.toString(), Transaction.saveList(v)));
        tag.put("ledgers", ledgersTag);

        CompoundTag claims = new CompoundTag();
        claimedQuests.forEach((player, ids) -> {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (String id : ids) {
                list.add(list.size(), net.minecraft.nbt.StringTag.valueOf(id));
            }
            claims.put(player.toString(), list);
        });
        tag.put("claimedQuests", claims);

        tag.put("demand", demand.save());
        tag.putLong("nextTxId", txIdSeq.get());
        CompoundTag betsTag = new CompoundTag();
        bets.forEach((player, session) -> {
            if (!session.isActive()) return;
            CompoundTag run = new CompoundTag();
            run.putUUID("player", player.player());
            run.putLong("stake", session.stake());
            run.putUUID("wallet", session.wallet());
            run.putString("game", session.game());
            run.putDouble("maxMultiplier", session.maxMultiplier());
            run.putDouble("multiplier", session.multiplier());
            run.putInt("rounds", session.rounds());
            if (session.liveCrash()) { run.putLong("crashStarted", session.crashStarted()); run.putLong("crashDue", session.crashDue()); }
            if(session.worldBound()){run.putLong("machinePos",session.machinePos().asLong());run.putString("machineDimension",session.machineDimension());}
            betsTag.put(player.storageKey(), run);
        });
        tag.put("activeBets", betsTag);
        CompoundTag remainderTag = new CompoundTag();
        saleRemainders.forEach((wallet, value) -> remainderTag.putDouble(wallet.toString(), value));
        tag.put("saleRemainders", remainderTag);
        CompoundTag pending = new CompoundTag();
        pendingMachines.forEach((id, game) -> pending.put(id.storageKey(), game.save()));
        tag.put("pendingMachines", pending);
        CompoundTag tickets = new CompoundTag();
        scratchTickets.forEach((id,ticket)->tickets.put(id.toString(),ticket.save()));
        tag.put("scratchTickets",tickets);
        return tag;
    }

    // ---- balances ----------------------------------------------------------

    /** True once this wallet has been created (used for one-time starting balance grants). */
    public boolean hasWallet(UUID teamKey) {
        return balances.containsKey(teamKey);
    }

    public long getBalance(UUID teamKey) {
        return balances.getOrDefault(teamKey, 0L);
    }

    public int casinoLevel(UUID wallet) { return casinoLevels.getOrDefault(wallet, 1); }

    /** Personal admin exemption from this mod's advancement gates; never awards vanilla criteria. */
    public boolean bypassesProgression(UUID player) { return progressionBypasses.contains(player); }

    public void setProgressionBypass(UUID player, boolean enabled) {
        boolean changed = enabled ? progressionBypasses.add(player) : progressionBypasses.remove(player);
        if (changed) setDirty();
    }

    public void setCasinoLevel(UUID wallet, int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Level must be 1..5");
        casinoLevels.put(wallet, level);
        setDirty();
    }

    /** Expected-next-level check makes double clicks and concurrent team upgrades idempotent. */
    public boolean upgradeCasinoLevel(UUID wallet, int expectedNextLevel, long cost) {
        if (expectedNextLevel != casinoLevel(wallet) + 1 || expectedNextLevel > 5 || cost <= 0 || !debit(wallet, cost)) return false;
        casinoLevels.put(wallet, expectedNextLevel);
        setDirty();
        return true;
    }

    public void setBalance(UUID teamKey, long amount) {
        balances.put(teamKey, clampBalance(amount));
        setDirty();
    }

    /** Returns true when the team can pay this amount. */
    public boolean canAfford(UUID teamKey, long amount) {
        return amount >= 0 && getBalance(teamKey) >= amount;
    }

    private long clampBalance(long amount) {
        amount = Math.min(MoneyMath.MAX_MONEY, amount);
        long cap = TeConfig.ECONOMY.maxBalance.get();
        if (cap > 0 && amount > cap) {
            return cap;
        }
        if (amount < 0) {
            return 0;
        }
        return amount;
    }

    // ---- ledger ------------------------------------------------------------
    /** Returns the actual credit after applying the wallet cap. */
    public long credit(UUID wallet, long amount) {
        long before = getBalance(wallet);
        setBalance(wallet, MoneyMath.add(before, amount));
        return getBalance(wallet) - before;
    }

    public boolean debit(UUID wallet, long amount) {
        if (!canAfford(wallet, amount)) return false;
        setBalance(wallet, getBalance(wallet) - amount);
        return true;
    }

    public boolean canCredit(UUID wallet, long amount) {
        long configured = TeConfig.ECONOMY.maxBalance.get();
        long cap = configured > 0 ? Math.min(configured, MoneyMath.MAX_MONEY) : MoneyMath.MAX_MONEY;
        return amount >= 0 && getBalance(wallet) <= cap - amount;
    }

    public double saleRemainder(UUID wallet) { return saleRemainders.getOrDefault(wallet, 0D); }
    public void setSaleRemainder(UUID wallet, double value) { saleRemainders.put(wallet, value); setDirty(); }

    public BetSession bet(UUID player) { return bet(SessionKey.terminal(player)); }
    public BetSession bet(SessionKey key) { return bets.get(key); }
    public Map<SessionKey,BetSession> activeBets(){return Map.copyOf(bets);}

    public PendingMachineGame pendingMachine(SessionKey key) { return pendingMachines.get(key); }
    /** Diagnostic helper only. Actions must address a specific machine. */
    public PendingMachineGame pendingMachine(UUID player) {
        return pendingMachines.values().stream().filter(p -> p.player().equals(player)).findFirst().orElse(null);
    }
    public com.evolt.teamecon.scratch.ScratchTicket ticket(UUID id) { return scratchTickets.get(id); }
    public void putTicket(com.evolt.teamecon.scratch.ScratchTicket ticket) { scratchTickets.put(ticket.id(),ticket);setDirty(); }
    public void removeTicket(UUID id) { scratchTickets.remove(id);setDirty(); }
    public List<PendingMachineGame> pendingMachines() { return List.copyOf(pendingMachines.values()); }
    public boolean machineBusy(String dimension, net.minecraft.core.BlockPos pos) {
        return pendingMachines.values().stream().anyMatch(p -> p.dimension().equals(dimension) && p.pos().equals(pos));
    }
    public void putPendingMachine(PendingMachineGame game) { pendingMachines.put(SessionKey.of(game), game); setDirty(); }
    public PendingMachineGame removePendingMachine(SessionKey key) { var value = pendingMachines.remove(key); setDirty(); return value; }

    public void putBet(UUID player, BetSession session) {
        bets.put(SessionKey.of(player, session), session);
        setDirty();
    }

    public void removeBet(UUID player) { removeBet(SessionKey.terminal(player)); }

    public void removeBet(SessionKey key) {
        bets.remove(key);
        setDirty();
    }

    public List<Transaction> ledger(UUID teamKey) {
        return Collections.unmodifiableList(ledgers.getOrDefault(teamKey, Collections.emptyList()));
    }

    public TeamStats stats(UUID teamKey) {
        return stats.computeIfAbsent(teamKey, k -> new TeamStats());
    }

    public void appendTransaction(Transaction tx) {
        List<Transaction> ledger = ledgers.computeIfAbsent(tx.teamKey(), k -> new ArrayList<>());
        int limit = TeConfig.ECONOMY.transactionLogLimit.get();
        if (limit > 0 && ledger.size() >= limit) {
            ledger.remove(0);
        }
        ledger.add(tx);
        stats(tx.teamKey()).apply(tx);
        setDirty();
    }

    public long nextTxId() {
        return txIdSeq.getAndIncrement();
    }

    /** Refund handler: writes a reversing row and rebuilds aggregates from the ledger. */
    public void refund(long originalTxId, UUID teamKey, UUID playerId, String playerName) {
        List<Transaction> ledger = ledgers.get(teamKey);
        if (ledger == null) {
            return;
        }
        Transaction original = null;
        for (Transaction tx : ledger) {
            if (tx.id() == originalTxId) {
                original = tx;
                break;
            }
        }
        if (original == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Transaction reverse = new Transaction(
                nextTxId(), teamKey, playerId, playerName, TxType.REFUND,
                original.itemKey(), original.quantity(), -original.unitPrice(), -original.total(), now);
        long newBalance = clampBalance(getBalance(teamKey) - original.total());
        setBalance(teamKey, newBalance);
        appendTransaction(reverse);
        stats(teamKey).recompute(ledger);
        setDirty();
    }

    // ---- quest claims ------------------------------------------------------

    public boolean hasClaimed(UUID playerId, String questId) {
        return claimedQuests.getOrDefault(playerId, Set.of()).contains(questId);
    }

    public boolean claim(UUID playerId, String questId) {
        Set<String> ids = claimedQuests.computeIfAbsent(playerId, k -> new HashSet<>());
        if (ids.contains(questId)) {
            return false;
        }
        ids.add(questId);
        setDirty();
        return true;
    }

    public void unclaim(UUID playerId, String questId) {
        Set<String> ids = claimedQuests.get(playerId);
        if (ids != null) {
            ids.remove(questId);
            setDirty();
        }
    }

    // ---- market ------------------------------------------------------------

    public DemandState demand() {
        return demand;
    }
}
