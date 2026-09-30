package com.evolt.teamecon.client;

import com.evolt.teamecon.network.payloads.CasinoSyncPayload;

/**
 * Client-side cache of the last casino state the server sent. The screen reads from here;
 * it never predicts outcomes locally.
 */
public final class CasinoScreenData {

    private static volatile CasinoSyncPayload last;

    private CasinoScreenData() {
    }

    public static void update(CasinoSyncPayload payload) {
        last = payload;
    }

    public static CasinoSyncPayload last() {
        return last;
    }

    public static void updateBalance(int containerId, long balance, String name) {
        if (last != null && last.containerId() == containerId) last = last.withBalance(balance, name);
    }

    public static void clear() { last = null; }
}
