package com.evolt.teamecon.gambling;

import com.evolt.teamecon.economy.MoneyMath;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Durable settlement instruction: unloading or breaking a machine cannot lose or duplicate money. */
public record PendingMachineGame(UUID player, UUID wallet, String playerName, String game, String dimension,
        BlockPos pos, long dueTick, Kind kind, long stake, long payout, double multiplier, String extra,
        boolean won, RiskTier tier, int expectedRound) {
    public enum Kind { INSTANT, ROUND, CASH_OUT }
    public PendingMachineGame {
        pos = pos.immutable();
        if (stake < 0 || payout < 0 || payout > MoneyMath.MAX_MONEY || !Double.isFinite(multiplier)
                || multiplier < 0 || (kind == Kind.ROUND && (tier == null || !tier.isValid())))
            throw new IllegalArgumentException("Invalid pending machine game");
    }
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("player", player); t.putUUID("wallet", wallet); t.putString("name", playerName);
        t.putString("game", game); t.putString("dimension", dimension); t.putLong("pos", pos.asLong());
        t.putLong("due", dueTick); t.putString("kind", kind.name()); t.putLong("stake", stake);
        t.putLong("payout", payout); t.putDouble("multiplier", multiplier); t.putString("extra", extra);
        t.putBoolean("won", won); t.putInt("round", expectedRound);
        if (tier != null) { t.putString("tier", tier.name()); t.putDouble("chance", tier.successChance()); t.putDouble("gain", tier.gain()); }
        return t;
    }
    public static PendingMachineGame load(CompoundTag t) {
        Kind kind = Kind.valueOf(t.getString("kind"));
        return new PendingMachineGame(t.getUUID("player"), t.getUUID("wallet"), t.getString("name"), t.getString("game"),
                t.getString("dimension"), BlockPos.of(t.getLong("pos")), t.getLong("due"), kind, t.getLong("stake"),
                t.getLong("payout"), t.getDouble("multiplier"), t.getString("extra"), t.getBoolean("won"),
                kind == Kind.ROUND ? new RiskTier(t.getString("tier"), t.getDouble("chance"), t.getDouble("gain")) : null, t.getInt("round"));
    }
}
