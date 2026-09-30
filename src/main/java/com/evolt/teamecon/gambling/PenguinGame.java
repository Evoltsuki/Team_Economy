package com.evolt.teamecon.gambling;

import java.util.List;

/** One jump advances exactly one layer; each successful layer raises the total payout. */
public final class PenguinGame {
    public static final double STEP_RETURN = .96D;
    private static final double[] LAYERS = {1, 1.2, 1.5, 2, 2.75, 4, 6, 10, 16, 25, 40};
    private PenguinGame() {}
    public static double layerMultiplier(int layer) {
        int n = Math.max(0, layer);
        return n < LAYERS.length ? LAYERS[n] : Math.min(1_000_000, 40 * Math.pow(1.6, n - 10));
    }
    public static RiskTier next(int rounds, double current, double cap, double chanceScale) {
        double target = Math.min(cap, Math.max(layerMultiplier(rounds + 1), current * 1.2));
        double factor = target / current;
        // The risk denominator keeps growing even when the final jump is shortened by a cap.
        double risk = Math.max(factor, 1.2D + .08D * Math.max(0, rounds));
        return new RiskTier("jump", STEP_RETURN * chanceScale / risk, factor - 1);
    }
    public static List<RiskTier> jumps() { return List.of(next(0, 1, 40, 1)); }
    public static RiskTier jump(String id) { return "jump".equals(id) ? jumps().getFirst() : null; }
}
