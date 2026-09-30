package com.evolt.teamecon.market;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Demand "dip" for sold goods. The dip for a material grows with every unit sold and
 * recovers over real time; the price multiplier is a pure function of the dip.
 * <p>
 * Prices are marginal: the price of unit i only looks at the dip after i-1 units. That
 * makes one big sale and the same items sold one at a time sum to the same value before
 * rounding, so splitting or merging an order is never a money printer.
 */
public final class DemandState {

    public static final UUID SERVER_SCOPE = new UUID(0L, 0L);

    private final MarketParams params;
    private final Map<UUID, Map<String, Dip>> dips = new HashMap<>();

    public DemandState(MarketParams params) {
        this.params = params;
    }

    /** Demand pool for a team: per team when configured, otherwise shared server-wide. */
    public UUID scopeFor(UUID teamKey) {
        return params.demandScopeIsTeam() ? teamKey : SERVER_SCOPE;
    }

    public double currentDip(UUID scope, String group, long nowMillis) {
        Dip dip = dips.getOrDefault(scope, Map.of()).get(group);
        return dip == null ? 0D : dip.recovered(nowMillis, params.recoverySecondsPerUnit());
    }

    /**
     * Total price multiplier for adding {@code units} of demand to a group, computed as the
     * integral of the price curve over the interval it consumes.
     * <p>
     * Being an integral makes the payout a function of only the start and end demand state,
     * so splitting a sale, selling in another unit (block vs ingots vs nuggets) or converting
     * a material back and forth always yields exactly the same money: no partition of the
     * demand interval can pay more than the whole. Callers must call {@link #addDip}
     * afterwards with the same amount of units.
     */
    public double factorIntegral(UUID scope, String group, double units, long nowMillis) {
        return factorIntegralAfter(scope, group, 0, units, nowMillis);
    }

    /** Read-only quote after earlier stacks in the same batch consumed this material group. */
    public double factorIntegralAfter(UUID scope, String group, double offset, double units, long nowMillis) {
        if (units <= 0D) {
            return 0D;
        }
        double start = currentDip(scope, group, nowMillis) + Math.max(0, offset);
        double cap = params.maxDipUnits();
        if (cap > 0) start = Math.min(start, cap);
        if (cap > 0 && start + units > cap) {
            double beforeCap = Math.max(0D, cap - start);
            double cappedFactor = Math.max(params.minPriceFactor(), Math.pow(1D - params.decayPerUnit(), cap));
            return prefix(start + beforeCap) - prefix(start) + (units - beforeCap) * cappedFactor;
        }
        return prefix(start + units) - prefix(start);
    }

    /** Antiderivative of the price curve f(d) = max(minFactor, (1-decay)^d). */
    private double prefix(double d) {
        if (d <= 0D) {
            return 0D;
        }
        double decay = params.decayPerUnit();
        double min = params.minPriceFactor();
        if (decay <= 0D || min >= 1D) {
            return d;
        }
        if (decay >= 1D) return min * d;
        double a = 1D - decay;
        double crossOver = (min > 0D && min < 1D) ? Math.log(min) / Math.log(a) : Double.POSITIVE_INFINITY;
        if (d <= crossOver) {
            return (1D - Math.pow(a, d)) / (-Math.log(a));
        }
        return (1D - min) / (-Math.log(a)) + min * (d - crossOver);
    }

    public void addDip(UUID scope, String group, double units, long nowMillis) {
        shift(scope, group, units, nowMillis, params.maxDipUnits(), true);
    }

    /** Reverses dip from a previous sale; used by refunds. */
    public void removeDip(UUID scope, String group, double units, long nowMillis) {
        shift(scope, group, units, nowMillis, 0D, false);
    }

    private void shift(UUID scope, String group, double units, long nowMillis, double cap, boolean add) {
        if (units == 0D) {
            return;
        }
        Dip dip = dips.computeIfAbsent(scope, k -> new HashMap<>()).computeIfAbsent(group, k -> new Dip());
        double current = dip.recovered(nowMillis, params.recoverySecondsPerUnit());
        dip.units = add ? (cap > 0 ? Math.min(current + units, cap) : current + units) : Math.max(0D, current - units);
        dip.lastUpdate = nowMillis;
    }

    private static final class Dip {
        private double units;
        private long lastUpdate;

        double recovered(long nowMillis, double perUnitSeconds) {
            double elapsedSeconds = Math.max(0L, nowMillis - lastUpdate) / 1000D;
            return Math.max(0D, units - elapsedSeconds / perUnitSeconds);
        }

        net.minecraft.nbt.CompoundTag save() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putDouble("units", units);
            tag.putLong("lastUpdate", lastUpdate);
            return tag;
        }

        static Dip load(net.minecraft.nbt.CompoundTag tag) {
            Dip dip = new Dip();
            dip.units = tag.getDouble("units");
            dip.lastUpdate = tag.getLong("lastUpdate");
            return dip;
        }
    }

    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        dips.forEach((scope, items) -> {
            net.minecraft.nbt.CompoundTag scopeTag = new net.minecraft.nbt.CompoundTag();
            items.forEach((group, dip) -> scopeTag.put(group, dip.save()));
            tag.put(scope.toString(), scopeTag);
        });
        return tag;
    }

    /** Loads this state in place from saved NBT. */
    public void loadInto(net.minecraft.nbt.CompoundTag tag) {
        dips.clear();
        for (String scopeKey : tag.getAllKeys()) {
            net.minecraft.nbt.CompoundTag scopeTag = tag.getCompound(scopeKey);
            Map<String, Dip> items = dips.computeIfAbsent(UUID.fromString(scopeKey), k -> new HashMap<>());
            for (String group : scopeTag.getAllKeys()) {
                if (scopeTag.get(group) instanceof net.minecraft.nbt.CompoundTag ct) {
                    items.put(group, Dip.load(ct));
                }
            }
        }
    }
}
