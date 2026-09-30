package com.evolt.teamecon.gambling;

import net.minecraft.core.BlockPos;
import java.util.UUID;

/** A terminal has one run per player; each physical cabinet has its own run. */
public record SessionKey(UUID player, String dimension, BlockPos pos) {
    public SessionKey {
        java.util.Objects.requireNonNull(player);
        java.util.Objects.requireNonNull(dimension);
        if (pos != null) pos = pos.immutable();
        if ((pos == null) != dimension.isEmpty()) throw new IllegalArgumentException("Invalid session location");
    }
    public static SessionKey terminal(UUID player) { return new SessionKey(player, "", null); }
    public static SessionKey machine(UUID player, String dimension, BlockPos pos) {
        return new SessionKey(player, dimension, java.util.Objects.requireNonNull(pos));
    }
    public static SessionKey of(UUID player, BetSession session) {
        return session.worldBound() ? machine(player, session.machineDimension(), session.machinePos()) : terminal(player);
    }
    public static SessionKey of(PendingMachineGame game) { return machine(game.player(), game.dimension(), game.pos()); }
    public String storageKey() { return pos == null ? player.toString() : player + "|" + dimension + "|" + pos.asLong(); }
}
