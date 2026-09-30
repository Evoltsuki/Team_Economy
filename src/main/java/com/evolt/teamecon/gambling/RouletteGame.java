package com.evolt.teamecon.gambling;

import java.util.Set;

/** European single-zero roulette. Returns include the original stake. */
public final class RouletteGame {
    public static final int POCKETS = 37;
    public static final double EXPECTED_FACTOR = 36D / POCKETS;
    public static final double STRAIGHT_EXPECTED_FACTOR = 32D / POCKETS;
    public static final int[] WHEEL = {0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,
            5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};
    private static final Set<Integer> RED = Set.of(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36);
    private RouletteGame() {}

    public static boolean isRed(int number) { return RED.contains(number); }

    public static boolean validChoice(String choice) {
        if (choice == null) return false;
        return switch (choice) {
            case "red", "black", "even", "odd", "low", "high" -> true;
            default -> selectedNumber(choice) >= 0;
        };
    }

    public static int selectedNumber(String choice) {
        if (choice == null || !choice.startsWith("number:")) return -1;
        try {
            int number = Integer.parseInt(choice.substring(7));
            return number >= 0 && number < POCKETS ? number : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    public static double multiplier(String choice, int number) {
        if (number < 0 || number >= POCKETS || !validChoice(choice)) return 0;
        int selected = selectedNumber(choice);
        if (selected >= 0) return selected == number ? 32D : 0D;
        if (number == 0) return 0D;
        boolean win = switch (choice) {
            case "red" -> isRed(number);
            case "black" -> !isRed(number);
            case "even" -> number % 2 == 0;
            case "odd" -> number % 2 != 0;
            case "low" -> number <= 18;
            case "high" -> number >= 19;
            default -> false;
        };
        return win ? 2D : 0D;
    }
}
