package com.evolt.teamecon.client;

import com.evolt.teamecon.gambling.CrashGame;

/** Reconstructs already observed growth, without the hidden crash deadline. */
record CrashChart(double peak, double seconds, double horizon, double ceiling) {
    static CrashChart observed(double value) {
        double peak=Double.isFinite(value)?Math.clamp(value,1D,1_000_000D):1D;
        double seconds=CrashGame.limitAfter(peak)/20D;
        return new CrashChart(peak,seconds,Math.max(6,Math.ceil(seconds/6)*6),Math.max(1.5,Math.ceil(peak*1.1*2)/2));
    }
    double x(double fraction){return seconds*fraction/horizon;}
    double y(double fraction){return CrashGame.multiplier(Math.round(seconds*20*fraction),peak)/ceiling;}
}
