package com.evolt.teamecon.economy;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated per-team figures used by the leaderboards.
 * Values are kept in sync with the ledger; refunds recompute them from the stored rows
 * so leaderboards never show money that was rolled back.
 */
public final class TeamStats {

    private final Map<UUID, Long> earningsByPlayer = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    public Map<UUID, Long> earningsByPlayer(){return java.util.Collections.unmodifiableMap(earningsByPlayer);}
    public String playerName(UUID id){return playerNames.getOrDefault(id,id.toString().substring(0,8));}
    private long totalSold;
    private long totalSpent;
    private long biggestTransaction;
    private long biggestSingleItemSale;
    private final Map<UUID, Long> salesByPlayer = new HashMap<>();
    private final Map<UUID, Long> spentByPlayer = new HashMap<>();

    public long totalSold() {
        return totalSold;
    }

    public long totalSpent() {
        return totalSpent;
    }

    public long biggestTransaction() {
        return biggestTransaction;
    }

    public long biggestSingleItemSale() {
        return biggestSingleItemSale;
    }

    public Map<UUID, Long> salesByPlayer() {
        return salesByPlayer;
    }

    public Map<UUID, Long> spentByPlayer() {
        return spentByPlayer;
    }

    /**
     * Incremental update for a fresh ledger row.
     * REFUND rows carry negative totals, so they subtract exactly what they reverse.
     */
    public void apply(Transaction tx) {
        TxType type = tx.type();
        playerNames.put(tx.playerId(),tx.playerName());
        // Transaction totals already carry their sign, including purchases and upgrades.
        if(type!=TxType.ADMIN)
            earningsByPlayer.merge(tx.playerId(),tx.total(),(a,b)->Math.clamp(a+b,-com.evolt.teamecon.economy.MoneyMath.MAX_MONEY,com.evolt.teamecon.economy.MoneyMath.MAX_MONEY));
        if (type == TxType.SELL || type == TxType.REFUND) {
            totalSold += tx.total();
            if (type == TxType.SELL) {
                biggestTransaction = Math.max(biggestTransaction, tx.total());
                if (tx.quantity() > 0) {
                    biggestSingleItemSale = Math.max(biggestSingleItemSale, tx.unitPrice());
                }
                salesByPlayer.merge(tx.playerId(), tx.total(), Long::sum);
            }
        } else if (type == TxType.BUY || type == TxType.SERVICE || type == TxType.BLINDBOX
                || type == TxType.GAMBLE_LOSS || type == TxType.GAMBLE_WIN) {
            // Wins and losses both count as consumption for the spending board.
            totalSpent += tx.total();
            spentByPlayer.merge(tx.playerId(), tx.total(), Long::sum);
        }
    }

    /** Full rebuild from the ledger; used after refunds and rollbacks. */
    public void recompute(List<Transaction> ledger) {
        totalSold = 0;
        totalSpent = 0;
        biggestTransaction = 0;
        biggestSingleItemSale = 0;
        earningsByPlayer.clear();playerNames.clear();
        salesByPlayer.clear();
        spentByPlayer.clear();
        for (Transaction tx : ledger) {
            apply(tx);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag earnings=new CompoundTag(), names=new CompoundTag();
        earningsByPlayer.forEach((id,value)->earnings.putLong(id.toString(),value));
        playerNames.forEach((id,value)->names.putString(id.toString(),value));
        tag.put("earningsByPlayer",earnings);tag.put("playerNames",names);
        tag.putInt("earningsVersion", 2);
        tag.putLong("totalSold", totalSold);
        tag.putLong("totalSpent", totalSpent);
        tag.putLong("biggestTransaction", biggestTransaction);
        tag.putLong("biggestSingleItemSale", biggestSingleItemSale);
        CompoundTag sales = new CompoundTag();
        salesByPlayer.forEach((k, v) -> sales.putLong(k.toString(), v));
        tag.put("salesByPlayer", sales);
        CompoundTag spent = new CompoundTag();
        spentByPlayer.forEach((k, v) -> spent.putLong(k.toString(), v));
        tag.put("spentByPlayer", spent);
        return tag;
    }

    public static TeamStats load(CompoundTag tag) {
        TeamStats stats = new TeamStats();
        stats.totalSold = tag.getLong("totalSold");
        stats.totalSpent = tag.getLong("totalSpent");
        stats.biggestTransaction = tag.getLong("biggestTransaction");
        stats.biggestSingleItemSale = tag.getLong("biggestSingleItemSale");
        CompoundTag sales = tag.getCompound("salesByPlayer");
        for (String key : sales.getAllKeys()) {
            stats.salesByPlayer.put(UUID.fromString(key), sales.getLong(key));
        }
        CompoundTag spent = tag.getCompound("spentByPlayer");
        for (String key : spent.getAllKeys()) {
            stats.spentByPlayer.put(UUID.fromString(key), spent.getLong(key));
        }
        CompoundTag earnings=tag.getCompound("earningsByPlayer"), names=tag.getCompound("playerNames");
        for(String id:earnings.getAllKeys())stats.earningsByPlayer.put(UUID.fromString(id),earnings.getLong(id));
        for(String id:names.getAllKeys())stats.playerNames.put(UUID.fromString(id),names.getString(id));
        if(!tag.contains("earningsByPlayer"))stats.earningsByPlayer.putAll(stats.salesByPlayer);
        if(tag.getInt("earningsVersion") < 2) {
            // Old earnings included games but omitted shopping. Recover lifetime shopping
            // from the saved spending aggregate, even when the ledger has been truncated.
            java.util.Set<UUID> ids = new java.util.HashSet<>(stats.earningsByPlayer.keySet());
            ids.addAll(stats.spentByPlayer.keySet());
            for (UUID id : ids) stats.earningsByPlayer.put(id, Math.clamp(
                    stats.salesByPlayer.getOrDefault(id, 0L) + stats.spentByPlayer.getOrDefault(id, 0L),
                    -MoneyMath.MAX_MONEY, MoneyMath.MAX_MONEY));
        }
        return stats;
    }
}
