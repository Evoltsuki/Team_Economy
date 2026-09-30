package com.evolt.teamecon.gambling;

/** Server-clock crash game. Only the current value is sent to clients, never the crash time. */
public final class CrashGame {
    public static final double GROWTH_PER_TICK = .0045D;
    public static final double ACCELERATION = .000012D;
    public static final double TAIL_START = 10D, TAIL_EXPONENT = 1.7D;
    private CrashGame() {}

    public static double multiplier(long elapsedTicks, double limit) {
        double ticks = Math.max(0L, elapsedTicks);
        double value = Math.exp(Math.min(100D, ticks * GROWTH_PER_TICK + ticks * ticks * ACCELERATION));
        return Math.min(limit, Math.floor(value * 100D) / 100D);
    }

    /** P(survive to x) <= returnFactor / x; the atom at x=1 prevents a free instant refund. */
    public static long crashAfter(double uniform, double returnFactor, double limit) {
        if (!Double.isFinite(uniform) || uniform < 0 || uniform >= 1
                || !Double.isFinite(returnFactor) || returnFactor <= 0 || returnFactor > 1
                || !Double.isFinite(limit) || limit < 1 || limit > 1_000_000)
            throw new IllegalArgumentException("Invalid crash draw");
        double point = returnFactor / (1D - uniform);
        if (point > TAIL_START) point = TAIL_START * Math.pow(point / TAIL_START, 1D / TAIL_EXPONENT);
        if (point <= 1) return 0;
        if (point > limit) return limitAfter(limit) + 1;
        return Math.max(1, limitAfter(point));
    }

    public static long limitAfter(double limit) {
        double log = Math.log(limit);
        return (long) Math.ceil(2 * log / (Math.sqrt(GROWTH_PER_TICK * GROWTH_PER_TICK + 4 * ACCELERATION * log) + GROWTH_PER_TICK));
    }
}
