package com.evolt.teamecon.client;

import com.evolt.teamecon.network.payloads.ShopSyncPayload;
import java.util.*;

public final class ClientShopCache {
    public record ItemRow(String itemKey, long unitPrice, boolean unlocked, String lockReason) {}
    public record BoxPrize(String itemKey, int count, int weight) {}
    public record BoxRow(String poolId, long price, String lockReason, boolean unlocked, List<BoxPrize> prizes) {}
    private static final Map<Integer, List<String[]>> boxChunks = new TreeMap<>();
    public record EnchantRow(String offerId, String enchantmentId, int level, long price, String lockReason, boolean unlocked) {}
    public record PrizeRow(String itemKey, int count) {}
    private static List<PrizeRow> rewards = List.of();
    private static int containerId = -1;
    private static long balance, saleQuote, revision;
    private static String walletName = "", messageKey = "", messageArgs = "";
    private static List<ItemRow> items = List.of();
    private static List<BoxRow> boxes = List.of();
    private static List<EnchantRow> enchants = List.of();
    private static final Map<Integer, List<ItemRow>> chunks = new TreeMap<>();
    private ClientShopCache() {}

    public static void update(ShopSyncPayload p) {
        if (containerId != p.containerId() || p.chunkIndex() == 0) {
            clear();
            containerId = p.containerId();
        }
        balance = p.balance(); walletName = p.walletName(); saleQuote = p.saleQuote();
        messageKey = p.messageKey(); messageArgs = p.messageArgs();
        if (!p.rewards().isEmpty()) rewards = rows(p.rewards(), r -> new PrizeRow(r[0], Integer.parseInt(r[1])), 2);
        if (p.chunkIndex() >= 0) {
            chunks.put(p.chunkIndex(), rows(p.items(), r -> new ItemRow(r[0], Long.parseLong(r[1]), r[2].equals("1"), r[3]), 4));
            items = chunks.values().stream().flatMap(List::stream).toList();
            boxChunks.put(p.chunkIndex(), rows(p.boxes(), r -> r, 7));
            Map<String, BoxRow> grouped = new LinkedHashMap<>();
            for (var part : boxChunks.values()) for (var r : part) {
                try {
                    var prize = new BoxPrize(r[4], Integer.parseInt(r[5]), Integer.parseInt(r[6]));
                    var box = grouped.computeIfAbsent(r[0], id -> new BoxRow(id, Long.parseLong(r[1]), r[2], r[3].equals("1"), new ArrayList<>()));
                    box.prizes().add(prize);
                } catch (RuntimeException ignored) {}
            }
            boxes = grouped.values().stream().map(b -> new BoxRow(b.poolId(), b.price(), b.lockReason(), b.unlocked(), List.copyOf(b.prizes()))).toList();
        }
        if (p.chunkIndex() == 0) {
            enchants = rows(p.enchants(), r -> new EnchantRow(r[0], r[1], Integer.parseInt(r[2]),
                    Long.parseLong(r[3]), r[4], r[5].equals("1")), 6);
        }
        revision++;
    }

    private static <T> List<T> rows(String text, java.util.function.Function<String[], T> mapper, int fields) {
        List<T> out = new ArrayList<>();
        if (!text.isEmpty()) for (String line : text.split(";")) {
            String[] r = line.split(",", -1);
            if (r.length != fields) continue;
            try { out.add(mapper.apply(r)); } catch (RuntimeException ignored) {}
        }
        return List.copyOf(out);
    }
    public static void updateBalance(int id, long value, String name, long quote) {
        if (containerId == id) { balance = value; walletName = name; saleQuote = quote; }
    }
    public static void clear() {
        containerId = -1; balance = saleQuote = 0; walletName = messageKey = messageArgs = "";
        items = List.of(); boxes = List.of(); enchants = List.of(); rewards = List.of(); chunks.clear(); boxChunks.clear(); revision++;
    }
    public static int containerId() { return containerId; }
    public static long revision() { return revision; }
    public static long balance() { return balance; }
    public static long saleQuote() { return saleQuote; }
    public static String walletName() { return walletName; }
    public static String messageKey() { return messageKey; }
    public static String messageArgs() { return messageArgs; }
    public static List<ItemRow> items() { return items; }
    public static List<BoxRow> boxes() { return boxes; }
    public static List<EnchantRow> enchants() { return enchants; }
    public static List<PrizeRow> rewards() { return rewards; }
}
