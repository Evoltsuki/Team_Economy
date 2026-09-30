package com.evolt.teamecon.market;

import com.evolt.teamecon.config.TeConfig;

/**
 * Market tuning parameters. Kept separate from {@link TeConfig} so the math can be
 * exercised in plain unit tests without bootstrapping the config system.
 */
public record MarketParams(double decayPerUnit, double minPriceFactor,
                           double recoverySecondsPerUnit, int maxDipUnits,
                           boolean demandScopeIsTeam) {

    public static MarketParams fromConfig() {
        return new MarketParams(
                TeConfig.MARKET.decayPerUnit.get(),
                TeConfig.MARKET.minPriceFactor.get(),
                TeConfig.MARKET.recoverySecondsPerUnit.get(),
                TeConfig.MARKET.maxDipUnits.get(),
                TeConfig.ECONOMY.demandScopeIsTeam.get());
    }
}
