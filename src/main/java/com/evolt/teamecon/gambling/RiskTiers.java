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
 * The risk tiers offered by the multiplier betting game, loaded from a JSON config file so
 * server owners can rebalance them. Every tier is validated against the configured
 * expected-value cap on load: a tier that would print money in expectation is rejected.
 * <p>
 * A round either grows the multiplier by (1 + gain) or busts the run, so the expected factor
 * is successChance * (1 + gain).
 */
public final class RiskTiers {

    private static final String FILE_NAME = "teamecon_risk_tiers.json";

    private List<RiskTier> tiers = new ArrayList<>();

    public List<RiskTier> all() {
        return List.copyOf(tiers);
    }

    public RiskTier byName(String name) {
        for (RiskTier tier : tiers) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }

    public void load(Path configDir) {
        load(configDir, TeConfig.GAMBLING.expectedValueCap.get());
    }

    void load(Path configDir, double cap) {
        Path file = configDir.resolve(FILE_NAME);
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);
                tiers.clear();
                if (element instanceof JsonArray array) {
                    for (JsonElement entry : array) {
                        if (entry instanceof JsonObject obj) {
                            RiskTier tier = new RiskTier(
                                    obj.get("name").getAsString(),
                                    obj.get("successChance").getAsDouble(),
                                    obj.get("gain").getAsDouble());
                            tiers.add(tier);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                tiers.clear();
                ModLogger.error("Failed to read risk tiers; game disabled", e);
            }
        } else {
            defaults();
            writeDefaults(file);
        }
        validate(cap);
    }

    private void defaults() {
        tiers.clear();
        // Expected factor is successChance * (1 + gain); every tier stays at or below 1.0,
        // so no tier is a money printer. Higher tiers pay more but bust far more often.
        tiers.add(new RiskTier("low", 0.80D, 0.20D));    // 0.96
        tiers.add(new RiskTier("medium", 0.55D, 0.70D)); // 0.935
        tiers.add(new RiskTier("high", 0.35D, 1.60D));   // 0.91
        tiers.add(new RiskTier("extreme", 0.20D, 3.50D));// 0.90
    }

    private void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < tiers.size(); i++) {
                RiskTier t = tiers.get(i);
                sb.append("  {\"name\": \"").append(t.name()).append("\", ")
                        .append("\"successChance\": ").append(t.successChance()).append(", ")
                        .append("\"gain\": ").append(t.gain()).append("}")
                        .append(i < tiers.size() - 1 ? "," : "").append("\n");
            }
            sb.append("]\n");
            Files.writeString(file, sb.toString());
            ModLogger.info("Wrote default risk tiers to {}", file);
        } catch (IOException e) {
            ModLogger.error("Could not write default risk tiers", e);
        }
    }

    private void validate(double cap) {
        List<RiskTier> safe = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (RiskTier tier : tiers) {
            if (!tier.isValid() || !names.add(tier.name()) || safe.size() >= 8) {
                ModLogger.warn("Risk tier {} rejected: invalid probabilities", tier.name());
                continue;
            }
            double ev = tier.expectedFactor();
            if (ev > cap + 1e-9) {
                ModLogger.warn("Risk tier {} rejected: expected factor {} exceeds cap {}", tier.name(), ev, cap);
                continue;
            }
            safe.add(tier);
        }
        tiers = safe;
        ModLogger.info("Loaded {} risk tiers", tiers.size());
    }
}
