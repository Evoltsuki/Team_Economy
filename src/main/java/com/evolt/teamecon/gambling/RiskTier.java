package com.evolt.teamecon.gambling;

/**
 * One risk tier of the multiplier betting game.
 * <p>
 * A round at multiplier m succeeds with probability {@code successChance}: the multiplier
 * grows to m * (1 + gain). Otherwise the run busts: the multiplier drops to zero, the stake
 * is lost and the session ends. The expected multiplier factor of one round is therefore
 * successChance * (1 + gain), which must stay at or below 1.0 so the game can never print
 * money in expectation. Players cash out whenever they like, so the best expectation is the
 * starting multiplier, exactly like a real let-it-ride table.
 *
 * @param name          display key, e.g. "low"
 * @param successChance raw probability of a successful round, 0..1
 * @param gain          relative multiplier increase on success
 */
public record RiskTier(String name, double successChance, double gain) {

    /** Expected multiplier factor of a single round (1.0 means a perfectly fair game). */
    public double expectedFactor() {
        return successChance * (1D + gain);
    }

    public double applySuccess(double multiplier) {
        return multiplier * (1D + gain);
    }

    public boolean isValid() {
        return name != null && name.matches("[a-z0-9_]{1,32}")
                && Double.isFinite(successChance) && Double.isFinite(gain)
                && successChance >= 0D && successChance <= 1D && gain >= 0D && gain <= 1_000_000D;
    }
}
