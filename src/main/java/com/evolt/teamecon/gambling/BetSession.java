package com.evolt.teamecon.gambling;

import com.evolt.teamecon.economy.MoneyMath;
import java.util.UUID;

/**
 * One in-progress multiplier betting session. Lives entirely server-side: the client only
 * ever sees the results the server sends, and the session is cashed out exactly once.
 * <p>
 * A failure busts the run: the multiplier drops to zero and the session closes, so a losing
 * round ends the stake instead of dragging it down forever. The stake itself was deducted
 * when the run opened, so busting pays nothing back.
 */
public final class BetSession {

    private final long stake;
    private final UUID wallet;
    private final String game;
    private final double maxMultiplier;
    private double multiplier;
    private int rounds;
    private boolean active;
    private boolean busted;
    private String machineDimension="";
    private net.minecraft.core.BlockPos machinePos;
    private long crashStarted = -1, crashDue = -1;

    public BetSession(long stake) {
        this(stake, new UUID(0, 0), "multiplier", 1_000_000D);
    }

    public BetSession(long stake, UUID wallet, String game, double maxMultiplier) {
        if (stake <= 0 || stake > MoneyMath.MAX_MONEY || wallet == null
                || (!"multiplier".equals(game) && !"penguin".equals(game))
                || !Double.isFinite(maxMultiplier) || maxMultiplier < 1 || maxMultiplier > 1_000_000D)
            throw new IllegalArgumentException("Invalid betting session");
        this.stake = stake;
        this.wallet = wallet;
        this.game = game;
        this.maxMultiplier = maxMultiplier;
        this.multiplier = 1.0D;
        this.rounds = 0;
        this.active = true;
        this.busted = false;
    }

    public static BetSession restore(long stake, UUID wallet, String game, double maxMultiplier,
                                     double multiplier, int rounds) {
        BetSession session = new BetSession(stake, wallet, game, maxMultiplier);
        if (!Double.isFinite(multiplier) || multiplier < 1 || multiplier > maxMultiplier || rounds < 0)
            throw new IllegalArgumentException("Invalid saved run");
        session.multiplier = multiplier;
        session.rounds = rounds;
        return session;
    }

    public UUID wallet() { return wallet; }
    public boolean worldBound(){return machinePos!=null;}
    public String machineDimension(){return machineDimension;}
    public net.minecraft.core.BlockPos machinePos(){return machinePos;}
    public boolean atMachine(String dimension,net.minecraft.core.BlockPos pos){return worldBound()&&machineDimension.equals(dimension)&&machinePos.equals(pos);}
    public void bindMachine(String dimension,net.minecraft.core.BlockPos pos){machineDimension=dimension;machinePos=pos.immutable();}
    public String game() { return game; }
    public double maxMultiplier() { return maxMultiplier; }
    public boolean atLimit() { return multiplier >= maxMultiplier || rounds >= 1000; }

    public long stake() {
        return stake;
    }

    public double multiplier() {
        return multiplier;
    }

    public int rounds() {
        return rounds;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isBusted() {
        return busted;
    }

    public boolean liveCrash() { return game.equals("multiplier") && crashStarted >= 0; }
    public long crashStarted() { return crashStarted; }
    public long crashDue() { return crashDue; }
    public void startCrash(long now, long after) {
        if (!game.equals("multiplier") || now < 0 || after < 0 || after > CrashGame.limitAfter(maxMultiplier) + 1)
            throw new IllegalArgumentException("Invalid crash clock");
        crashStarted = now;
        crashDue = now + after;
    }
    public void restoreCrash(long start, long due) { startCrash(start, due - start); }
    /** Must run before every cash-out as well as every server tick. */
    public boolean advanceCrash(long now) {
        if (!active || !liveCrash()) return false;
        if (now >= crashDue) {
            multiplier = 0; active = false; busted = true;
            return true;
        }
        multiplier = CrashGame.multiplier(now - crashStarted, maxMultiplier);
        return false;
    }

    /** Value the player would receive right now; rounded down so the house never overpays. */
    public long cashOutValue() {
        return MoneyMath.payout(stake, multiplier);
    }

    public void applySuccess(RiskTier tier) {
        if (!active || atLimit()) return;
        if (!tier.isValid()) throw new IllegalArgumentException("Invalid risk tier");
        multiplier = Math.min(maxMultiplier, tier.applySuccess(multiplier));
        rounds++;
    }

    /** A failure zeroes the multiplier and ends the run; the stake stays with the house. */
    public void applyFailure(RiskTier tier) {
        if (!active || atLimit()) return;
        multiplier = 0.0D;
        rounds++;
        busted = true;
        active = false;
    }

    /** Pays out and closes the session. Repeated calls are no-ops. */
    public long close() {
        if (!active) return 0;
        long payout = cashOutValue();
        active = false;
        return payout;
    }
}
