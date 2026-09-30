package com.evolt.teamecon.gambling;

import com.evolt.teamecon.config.TeConfig;
import com.evolt.teamecon.util.ModLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Three-reel slot machine. Each reel is a weighted list of symbols; the payout table says what
 * a matching triple (or a consolation pair) is worth, as a multiple of the stake. The expected
 * payout is computed on load and rejected when it exceeds the configured cap, so the machine
 * can never be tuned into a money printer.
 */
public final class SlotsGame {

    private static final String FILE_NAME = "teamecon_slots.json";

    public record Symbol(String name, int weight) {
    }

    public record Payout(String symbol, int requiredMatches, double multiplier) {
    }

    private List<Symbol> reel = new ArrayList<>();
    private List<Payout> payouts = new ArrayList<>();

    public List<Symbol> reel() {
        return List.copyOf(reel);
    }

    public List<Payout> payouts() {
        return List.copyOf(payouts);
    }

    /** Symbols on one reel, or an empty list when the game is not configured. */
    public boolean ready() {
        return !reel.isEmpty() && !payouts.isEmpty();
    }

    public void load(Path configDir) {
        load(configDir, TeConfig.GAMBLING.expectedValueCap.get());
    }

    void load(Path configDir, double cap) {
        Path file = configDir.resolve(FILE_NAME);
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);
                reel.clear();
                payouts.clear();
                if (element instanceof JsonObject root) {
                    readReel(root);
                    readPayouts(root);
                    SlotsGame old = new SlotsGame(); old.legacyDefaults();
                    if (reel.equals(old.reel) && payouts.equals(old.payouts)) {
                        Path backup = file.resolveSibling(FILE_NAME + ".pre-pairs.bak");
                        if (!Files.exists(backup)) Files.copy(file, backup);
                        defaults();
                        writeDefaults(file);
                    }
                }
            } catch (IOException | RuntimeException e) {
                reel.clear();
                payouts.clear();
                ModLogger.error("Failed to read slots config; game disabled", e);
            }
        } else {
            defaults();
            writeDefaults(file);
        }
        validate(cap);
    }

    private void readReel(JsonObject root) {
        if (root.get("reel") instanceof JsonArray array) {
            for (JsonElement entry : array) {
                if (entry instanceof JsonObject obj) {
                    reel.add(new Symbol(
                            obj.get("symbol").getAsString(),
                            obj.get("weight").getAsInt()));
                }
            }
        }
    }

    private void readPayouts(JsonObject root) {
        if (root.get("payouts") instanceof JsonArray array) {
            for (JsonElement entry : array) {
                if (entry instanceof JsonObject obj) {
                    payouts.add(new Payout(
                            obj.get("symbol").getAsString(),
                            obj.get("matches").getAsInt(),
                            obj.get("multiplier").getAsDouble()));
                }
            }
        }
    }

    void defaults() {
        legacyDefaults();
        payouts.clear();
        double[] triples = {3, 4, 7, 16, 40, 200};
        for (int i = 0; i < reel.size(); i++) {
            payouts.add(new Payout(reel.get(i).name(), 3, triples[i]));
            payouts.add(new Payout(reel.get(i).name(), 2, 1.2D));
        }
    }

    void legacyDefaults() {
        reel.clear();
        payouts.clear();
        // Weights sum to 100; rarer symbols pay more.
        reel.add(new Symbol("cherry", 25));
        reel.add(new Symbol("lemon", 25));
        reel.add(new Symbol("orange", 20));
        reel.add(new Symbol("bell", 15));
        reel.add(new Symbol("star", 10));
        reel.add(new Symbol("diamond", 5));
        // Expected payout of this table is ~0.80, so the house keeps roughly 20%.
        payouts.add(new Payout("cherry", 3, 6D));
        payouts.add(new Payout("lemon", 3, 8D));
        payouts.add(new Payout("orange", 3, 13D));
        payouts.add(new Payout("bell", 3, 28D));
        payouts.add(new Payout("star", 3, 66D));
        payouts.add(new Payout("diamond", 3, 275D));
        payouts.add(new Payout("cherry", 2, 2D));
    }

    private void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"reel\": [\n");
            for (int i = 0; i < reel.size(); i++) {
                Symbol s = reel.get(i);
                sb.append("    {\"symbol\": \"").append(s.name()).append("\", \"weight\": ").append(s.weight()).append("}")
                        .append(i < reel.size() - 1 ? "," : "").append("\n");
            }
            sb.append("  ],\n  \"payouts\": [\n");
            for (int i = 0; i < payouts.size(); i++) {
                Payout p = payouts.get(i);
                sb.append("    {\"symbol\": \"").append(p.symbol()).append("\", \"matches\": ")
                        .append(p.requiredMatches()).append(", \"multiplier\": ").append(p.multiplier()).append("}")
                        .append(i < payouts.size() - 1 ? "," : "").append("\n");
            }
            sb.append("  ]\n}\n");
            Files.writeString(file, sb.toString());
            ModLogger.info("Wrote default slots config to {}", file);
        } catch (IOException e) {
            ModLogger.error("Could not write default slots config", e);
        }
    }

    private void validate(double cap) {
        if (reel.isEmpty() || payouts.isEmpty() || reel.size() > 16 || payouts.size() > 32) {
            reel.clear();
            ModLogger.warn("Slots game is empty, it stays disabled");
            return;
        }
        int totalWeight = 0;
        java.util.Set<String> symbols = new java.util.HashSet<>();
        for (Symbol s : reel) {
            if (s.weight() <= 0 || s.weight() > 1_000_000 || !s.name().matches("[a-z0-9_]{1,32}")
                    || !symbols.add(s.name())) {
                ModLogger.warn("Slots symbol {} rejected: bad weight", s.name());
                reel = new ArrayList<>();
                return;
            }
            totalWeight += s.weight();
        }
        for (Payout p : payouts) {
            if (!symbols.contains(p.symbol()) || (p.requiredMatches() != 2 && p.requiredMatches() != 3)
                    || !Double.isFinite(p.multiplier()) || p.multiplier() < 0 || p.multiplier() > 1_000_000D) {
                ModLogger.warn("Slots payout rejected: {}", p);
                reel.clear();
                return;
            }
        }
        double expected = expectedPayout(totalWeight);
        if (expected > cap + 1e-9) {
            ModLogger.warn("Slots table rejected: expected payout {} exceeds cap {}", expected, cap);
            reel.clear();
            return;
        }
        ModLogger.info("Slots game loaded: {} symbols, {} payouts, expected payout {}",
                reel.size(), payouts.size(), expected);
    }

    /** Probability-weighted payout multiplier of one spin. */
    double expectedPayout(int totalWeight) {
        double expected = 0D;
        if (totalWeight <= 0) return 0;
        // Compute the same maximum-of-matching-prizes rule used by a real spin.
        for (Symbol a : reel) for (Symbol b : reel) for (Symbol c : reel)
            expected += (a.weight() / (double) totalWeight) * (b.weight() / (double) totalWeight)
                    * (c.weight() / (double) totalWeight)
                    * payoutFor(new String[]{a.name(), b.name(), c.name()});
        return expected;
    }

    private int weightOf(String symbol) {
        for (Symbol s : reel) {
            if (s.name().equals(symbol)) {
                return s.weight();
            }
        }
        return 0;
    }

    /** Spins the three reels. Returns the symbols and the resulting payout multiplier. */
    public SpinResult spin(Random random, long stake) {
        if (!ready()) {
            return new SpinResult(List.of(), 0L);
        }
        int totalWeight = 0;
        for (Symbol s : reel) {
            totalWeight += s.weight();
        }
        String[] symbols = new String[3];
        for (int i = 0; i < 3; i++) {
            symbols[i] = drawSymbol(random, totalWeight);
        }
        double multiplier = payoutFor(symbols);
        long payout = com.evolt.teamecon.economy.MoneyMath.payout(stake, multiplier);
        return new SpinResult(List.of(symbols), payout);
    }

    private String drawSymbol(Random random, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        int accumulated = 0;
        for (Symbol s : reel) {
            accumulated += s.weight();
            if (roll < accumulated) {
                return s.name();
            }
        }
        return reel.get(reel.size() - 1).name();
    }

    public double payoutFor(String[] symbols) {
        double best = 0D;
        for (Payout p : payouts) {
            long matches = 0;
            for (String s : symbols) {
                if (s.equals(p.symbol())) {
                    matches++;
                }
            }
            if (matches >= p.requiredMatches() && p.multiplier() > best) {
                best = p.multiplier();
            }
        }
        return best;
    }

    public record SpinResult(List<String> symbols, long payout) {
    }
}
