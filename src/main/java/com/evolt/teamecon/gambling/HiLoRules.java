package com.evolt.teamecon.gambling;

/** One uniform ten-sided roll; shrinking the range does not change either side's chance. */
public final class HiLoRules {
    public static final int SIDES = 10;
    private HiLoRules() {}
    public static boolean wins(int roll, String choice) {
        if (roll < 1 || roll > SIDES) return false;
        return "high".equals(choice) ? roll > SIDES / 2
                : "low".equals(choice) && roll <= SIDES / 2;
    }
}
