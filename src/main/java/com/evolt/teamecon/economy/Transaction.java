package com.evolt.teamecon.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/**
 * One ledger entry: what happened, who did it, and how much moved.
 * Leaderboards and quest progress are computed from these rows.
 */
public final class Transaction {

    private long id;
    private final UUID teamKey;
    private final UUID playerId;
    private final String playerName;
    private final TxType type;
    private final String itemKey;
    private final int quantity;
    private final long unitPrice;
    private final long total;
    private final long timestamp;

    public Transaction(long id, UUID teamKey, UUID playerId, String playerName, TxType type,
                       String itemKey, int quantity, long unitPrice, long total, long timestamp) {
        this.id = id;
        this.teamKey = teamKey;
        this.playerId = playerId;
        this.playerName = playerName;
        this.type = type;
        this.itemKey = itemKey;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.total = total;
        this.timestamp = timestamp;
    }

    public long id() {
        return id;
    }

    public UUID teamKey() {
        return teamKey;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public TxType type() {
        return type;
    }

    public String itemKey() {
        return itemKey;
    }

    public int quantity() {
        return quantity;
    }

    public long unitPrice() {
        return unitPrice;
    }

    public long total() {
        return total;
    }

    public long timestamp() {
        return timestamp;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putUUID("team", teamKey);
        tag.putUUID("player", playerId);
        tag.putString("playerName", playerName);
        tag.putString("type", type.key());
        tag.putString("item", itemKey);
        tag.putInt("qty", quantity);
        tag.putLong("unit", unitPrice);
        tag.putLong("total", total);
        tag.putLong("ts", timestamp);
        return tag;
    }

    public static Transaction load(CompoundTag tag) {
        return new Transaction(
                tag.getLong("id"),
                tag.getUUID("team"),
                tag.getUUID("player"),
                tag.getString("playerName"),
                TxType.byKey(tag.getString("type")),
                tag.getString("item"),
                tag.getInt("qty"),
                tag.getLong("unit"),
                tag.getLong("total"),
                tag.getLong("ts"));
    }

    public static ListTag saveList(Iterable<Transaction> transactions) {
        ListTag list = new ListTag();
        for (Transaction tx : transactions) {
            list.add(list.size(), tx.save());
        }
        return list;
    }

    public static void readList(ListTag list, java.util.List<Transaction> out) {
        out.clear();
        for (Tag tag : list) {
            if (tag instanceof CompoundTag ct) {
                out.add(load(ct));
            }
        }
    }
}
