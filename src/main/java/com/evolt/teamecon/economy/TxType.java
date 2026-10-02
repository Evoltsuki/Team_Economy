package com.evolt.teamecon.economy;

/**
 * Kinds of movement recorded in the team ledger. Refunds must be able to reverse these.
 */
public enum TxType {
    SELL("sell"),
    BUY("buy"),
    SERVICE("service"),
    GAMBLE_WIN("gamble_win"),
    GAMBLE_LOSS("gamble_loss"),
    BLINDBOX("blindbox"),
    REFUND("refund"),
    QUEST("quest"),
    ADMIN("admin");

    private final String key;

    TxType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static TxType byKey(String key) {
        for (TxType t : values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        return ADMIN;
    }
}
