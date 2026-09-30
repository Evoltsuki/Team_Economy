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

/**
 * Scratch-card prize pool, loaded from JSON so the odds are public and server-configurable.
 * Each entry has a weight and a payout multiplier of the card price; the expected payout of
 * a card is the probability-weighted sum of payout multipliers, which must not exceed 1.0.
 * <p>
 * Payouts never include key progression items, so the pool cannot become a shortcut past
 * normal play: cards only ever pay out money-value (balance/chips).
 */
public final class ScratchPools {

    private static final String FILE_NAME = "teamecon_scratch_pool.json";

    public record Entry(double chance, double payoutMultiplier, String label) {
    }

    private List<Entry> entries = new ArrayList<>();

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public boolean ready() { return !entries.isEmpty(); }

    public void load(Path configDir) {
        load(configDir, TeConfig.GAMBLING.expectedValueCap.get());
    }

    void load(Path configDir, double cap) {
        Path file = configDir.resolve(FILE_NAME);
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);
                entries.clear();
                if (element instanceof JsonArray array) {
                    for (JsonElement item : array) {
                        if (item instanceof JsonObject obj && entries.size() < 32) {
                            entries.add(new Entry(
                                    obj.get("chance").getAsDouble(),
                                    obj.get("payoutMultiplier").getAsDouble(),
                                    obj.has("label") ? obj.get("label").getAsString() : "prize"));
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                entries.clear();
                ModLogger.error("Failed to read scratch pool; game disabled", e);
            }
        } else {
            defaults();
            writeDefaults(file);
        }
        validate(cap);
    }

    private void defaults() {
        entries.clear();
        // Expected payout = sum(chance * multiplier) = 0.835, i.e. a house edge of ~16.5%.
        entries.add(new Entry(0.600D, 0.0D, "nothing"));
        entries.add(new Entry(0.250D, 1.0D, "even"));
        entries.add(new Entry(0.100D, 2.0D, "double"));
        entries.add(new Entry(0.035D, 5.0D, "five"));
        entries.add(new Entry(0.012D, 10.0D, "ten"));
        entries.add(new Entry(0.003D, 30.0D, "jackpot"));
    }

    private void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                sb.append("  {\"chance\": ").append(e.chance()).append(", ")
                        .append("\"payoutMultiplier\": ").append(e.payoutMultiplier()).append(", ")
                        .append("\"label\": \"").append(e.label()).append("\"}")
                        .append(i < entries.size() - 1 ? "," : "").append("\n");
            }
            sb.append("]\n");
            Files.writeString(file, sb.toString());
            ModLogger.info("Wrote default scratch pool to {}", file);
        } catch (IOException e) {
            ModLogger.error("Could not write default scratch pool", e);
        }
    }

    private void validate(double cap) {
        double total = 0D;
        List<Entry> safe = new ArrayList<>();
        for (Entry entry : entries) {
            if (!Double.isFinite(entry.chance()) || !Double.isFinite(entry.payoutMultiplier())
                    || entry.chance() <= 0D || entry.payoutMultiplier() < 0D
                    || entry.payoutMultiplier() > 1_000_000D || !entry.label().matches("[a-z0-9_]{1,32}")) {
                ModLogger.warn("Scratch entry '{}' rejected: negative values", entry.label());
                continue;
            }
            total += entry.chance();
            safe.add(entry);
        }
        if (total <= 0D || !Double.isFinite(total)) {
            entries = new ArrayList<>();
            ModLogger.warn("Scratch pool is empty; game disabled");
            return;
        }
        // Normalise to probabilities, then check the expected payout.
        double expected = 0D;
        for (Entry entry : safe) {
            expected += (entry.chance() / total) * entry.payoutMultiplier();
        }
        if (expected > cap + 1e-9) {
            ModLogger.warn("Scratch pool rejected: expected payout {} exceeds cap {}", expected, cap);
            entries = new ArrayList<>();
            return;
        }
        final double weight = total;
        entries = new ArrayList<>(safe.stream().map(e ->
                new Entry(e.chance() / weight, e.payoutMultiplier(), e.label())).toList());
        ModLogger.info("Scratch pool loaded: {} entries, expected payout {}", safe.size(), expected);
    }

    /** Draws an outcome using the supplied random source. Probabilities are normalised. */
    public Entry draw(java.util.Random random) {
        if (!ready()) throw new IllegalStateException("Scratch game is disabled");
        double total = 0D;
        for (Entry entry : entries) {
            total += entry.chance();
        }
        double roll = random.nextDouble() * total;
        double accumulated = 0D;
        for (Entry entry : entries) {
            accumulated += entry.chance();
            if (roll < accumulated) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }
}
