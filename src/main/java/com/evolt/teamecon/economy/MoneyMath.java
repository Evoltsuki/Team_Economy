package com.evolt.teamecon.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Bounded point arithmetic shared by the wallet, games and shops. */
public final class MoneyMath {
    public static final long MAX_MONEY = 9_000_000_000_000_000L;
    public static final long MAX_PRICE = 1_000_000_000L;
    public static final int MAX_PURCHASE = 2304;

    private MoneyMath() {}

    public static long add(long balance, long amount) {
        if (amount < 0 || balance < 0) throw new IllegalArgumentException("Negative credit");
        return balance >= MAX_MONEY - amount ? MAX_MONEY : balance + amount;
    }

    public static long payout(long stake, double multiplier) {
        if (stake <= 0 || !Double.isFinite(multiplier) || multiplier <= 0) return 0;
        BigDecimal value = BigDecimal.valueOf(stake).multiply(BigDecimal.valueOf(multiplier));
        return value.min(BigDecimal.valueOf(MAX_MONEY)).setScale(0, RoundingMode.DOWN).longValue();
    }

    public static long buyPrice(long sellValue, double markup) {
        if (sellValue <= 0 || !Double.isFinite(markup) || markup < 1) return 0;
        return BigDecimal.valueOf(sellValue).multiply(BigDecimal.valueOf(markup))
                .min(BigDecimal.valueOf(MAX_MONEY)).setScale(0, RoundingMode.CEILING).longValue();
    }

    public static long total(long unit, int count) {
        if (unit <= 0 || count <= 0 || count > MAX_PURCHASE || unit > MAX_MONEY / count) return 0;
        return unit * count;
    }

    /** The integral is measured in canonical material units, not stack items. */
    public static long saleValue(long unit, double factorIntegral, double unitsPerItem) {
        if (unit <= 0 || !Double.isFinite(factorIntegral) || factorIntegral <= 0
                || !Double.isFinite(unitsPerItem) || unitsPerItem <= 0) return 0;
        double value = unit * factorIntegral / unitsPerItem;
        return (long) Math.floor(Math.min(MAX_MONEY, value));
    }

    public record Settlement(long points, double remainder) {}

    /** Carries sub-point income forward instead of losing it on every small sale. */
    public static Settlement settle(double value, double remainder) {
        if (!Double.isFinite(value) || value < 0 || !Double.isFinite(remainder) || remainder < 0 || remainder >= 1)
            throw new IllegalArgumentException("Invalid sale settlement");
        double total = Math.min(MAX_MONEY, value + remainder);
        long whole = (long) Math.floor(total + 1e-9);
        return new Settlement(whole, Math.max(0, total - whole));
    }
}
