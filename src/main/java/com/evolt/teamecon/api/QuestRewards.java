package com.evolt.teamecon.api;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;
import java.util.UUID;

/** Server-thread API for task authors. One reward ID can credit each team wallet once. */
public final class QuestRewards {
    public enum Status { AWARDED, ALREADY_CLAIMED, WALLET_FULL, UNAVAILABLE }
    public record Result(Status status, long credited) { }
    public static final long MAX_AMOUNT = MoneyMath.MAX_PRICE;
    private QuestRewards() { }

    public static String claimKey(String rewardId) {
        if (rewardId == null || rewardId.length() > 128) throw new IllegalArgumentException("Invalid reward ID");
        // Keep the existing bridge's claim keys so an update cannot award the same quest twice.
        if (rewardId.matches("[0-7][0-9a-fA-F]{15}")) return "ftbquest-v3/" + rewardId.toUpperCase(Locale.ROOT);
        if (rewardId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return "teamecon-reward/" + rewardId;
        throw new IllegalArgumentException("Use a namespaced reward ID or a 16-digit FTB reward ID");
    }

    /** Call on the player's server thread. Ordinary players do not gain command permission. */
    public static Result grant(ServerPlayer player, String rewardId, long amount) {
        if (!player.getServer().isSameThread()) throw new IllegalStateException("Quest rewards require the server thread");
        var mod = TeamEconomyMod.get();
        if (mod == null || mod.economy() == null) return new Result(Status.UNAVAILABLE, 0);
        var manager = mod.economy().manager();
        UUID wallet = TeamUtil.walletKey(player.getServer(), player.getUUID());
        Result result = grantToWallet(manager, wallet, rewardId, amount);
        if (result.status() == Status.AWARDED) {
            manager.appendTransaction(new Transaction(manager.nextTxId(), wallet, player.getUUID(), player.getName().getString(),
                    TxType.QUEST, rewardId, 1, result.credited(), result.credited(), System.currentTimeMillis()));
        }
        return result;
    }

    /** Lower-level entry for a resolved wallet; the caller must run on the server thread. */
    public static Result grantToWallet(TeamEconomyManager manager, UUID wallet, String rewardId, long amount) {
        String key = claimKey(rewardId);
        if (amount < 1 || amount > MAX_AMOUNT) throw new IllegalArgumentException("Invalid reward amount");
        synchronized (manager) {
            if (manager.hasClaimed(wallet, key)) return new Result(Status.ALREADY_CLAIMED, 0);
            if (!manager.canCredit(wallet, amount)) return new Result(Status.WALLET_FULL, 0);
            long before = manager.getBalance(wallet);
            try {
                long credited = manager.credit(wallet, amount);
                if (credited != amount || !manager.claim(wallet, key)) throw new IllegalStateException("Incomplete quest reward");
                return new Result(Status.AWARDED, credited);
            } catch (RuntimeException ex) {
                manager.setBalance(wallet, before); manager.unclaim(wallet, key); throw ex;
            }
        }
    }
}
