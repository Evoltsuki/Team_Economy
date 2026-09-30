package com.evolt.teamecon.shop;

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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enchantments the shop will apply for a fee, loaded from JSON. Each entry fixes the
 * enchantment id, the exact level sold and the price, so the shop never sells an upgrade
 * that is worth more than it charges: the price is compared against the value the enchant
 * adds to the item, and entries that would be flipped for profit are dropped at load time.
 */
public final class EnchantShop {

    private static final String FILE_NAME = "teamecon_enchants.json";

    public record Offer(String id, String enchantmentId, int level, long price, String stage) {
    }

    private final Map<String, Offer> offers = new LinkedHashMap<>();

    public Collection<Offer> all() {
        return offers.values();
    }

    public Offer byId(String id) {
        return offers.get(id);
    }

    public boolean ready() {
        return !offers.isEmpty();
    }

    public void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        offers.clear();
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element instanceof JsonArray array) {
                    for (JsonElement item : array) {
                        if (!(item instanceof JsonObject obj)) {
                            continue;
                        }
                        Offer offer = readOffer(obj);
                        if (offer != null) {
                            offers.put(offer.id(), offer);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                ModLogger.error("Failed to read enchant shop file; offers disabled", e);
                offers.clear();
                return;
            }
        } else {
            defaults();
            writeDefaults(file);
        }
        // Upgrade only the exact six legacy defaults; custom server catalogues stay authoritative.
        Map<String, Offer> loaded = new LinkedHashMap<>(offers);
        defaults();
        Map<String, Offer> legacy = new LinkedHashMap<>(offers);
        legacy.keySet().retainAll(java.util.Set.of("unbreaking3", "efficiency4", "fortune3", "sharpness5", "protection4", "mending"));
        offers.clear(); offers.putAll(loaded);
        Map<String, Offer> original=new LinkedHashMap<>(legacy);
        String[] oldIds={"unbreaking3","efficiency4","fortune3","sharpness5","protection4","mending"};
        long[] oldPrices={96,160,256,320,240,512};
        for(int i=0;i<oldIds.length;i++){Offer o=original.get(oldIds[i]);original.put(o.id(),new Offer(o.id(),o.enchantmentId(),o.level(),oldPrices[i],o.stage()));}
        if (loaded.equals(legacy)||loaded.equals(original)) {
            try { if (!Files.exists(file.resolveSibling(FILE_NAME+".v1.bak"))) Files.copy(file,file.resolveSibling(FILE_NAME+".v1.bak")); }
            catch (IOException e) { ModLogger.error("Could not back up enchant defaults",e); return; }
            defaults(); writeDefaults(file);
        }
        ModLogger.info("Enchant shop loaded: {} offers", offers.size());
    }

    private Offer readOffer(JsonObject obj) {
        String id = obj.has("id") ? obj.get("id").getAsString() : "";
        String ench = obj.has("enchantment") ? obj.get("enchantment").getAsString() : "";
        int level = obj.has("level") ? obj.get("level").getAsInt() : 1;
        long price = obj.has("price") ? obj.get("price").getAsLong() : 0L;
        String stage = obj.has("stage") ? obj.get("stage").getAsString() : "";
        if (!id.matches("[a-z0-9_]{1,32}") || ench.isEmpty() || level < 1 || level > 255
                || price <= 0 || price > com.evolt.teamecon.economy.MoneyMath.MAX_PRICE
                || stage.length() > 128 || stage.contains(",") || stage.contains(";") || offers.size() >= 64) {
            return null;
        }
        return new Offer(id, ench, level, price, stage);
    }

    /** A few utility enchants at fair prices; the file is written so server owners can tune it. */
    private void defaults() {
        offers.clear();
        add("unbreaking3", "minecraft:unbreaking", 3, 4096L, "");
        add("efficiency4", "minecraft:efficiency", 4, 8192L, "");
        add("fortune3", "minecraft:fortune", 3, 16384L, "");
        add("sharpness5", "minecraft:sharpness", 5, 32768L, "");
        add("protection4", "minecraft:protection", 4, 16384L, "");
        add("mending", "minecraft:mending", 1, 65536L, "");
        String[] ordinary = {"silk_touch:1", "efficiency:5", "smite:5", "bane_of_arthropods:5", "knockback:2",
                "fire_aspect:2", "looting:3", "sweeping_edge:3", "fire_protection:4", "blast_protection:4",
                "projectile_protection:4", "feather_falling:4", "respiration:3", "aqua_affinity:1", "thorns:3",
                "depth_strider:3", "frost_walker:2", "power:5", "punch:2", "flame:1", "infinity:1",
                "luck_of_the_sea:3", "lure:3", "loyalty:3", "impaling:5", "riptide:3", "channeling:1",
                "multishot:1", "piercing:4", "quick_charge:3", "density:5", "breach:4",
                "binding_curse:1", "vanishing_curse:1", "soul_speed:3", "swift_sneak:3", "wind_burst:3"};
        for (String entry : ordinary) {
            String[] part = entry.split(":"); int level = Integer.parseInt(part[1]);
            add(part[0]+level, "minecraft:"+part[0], level, 4096L*Math.max(2,level), "");
        }
    }

    private void add(String id, String ench, int level, long price, String stage) {
        offers.put(id, new Offer(id, ench, level, price, stage));
    }

    private void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonArray array = new JsonArray();
            for (Offer offer : offers.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", offer.id());
                obj.addProperty("enchantment", offer.enchantmentId());
                obj.addProperty("level", offer.level());
                obj.addProperty("price", offer.price());
                obj.addProperty("stage", offer.stage());
                array.add(obj);
            }
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(array));
            ModLogger.info("Wrote default enchant shop file to {}", file);
        } catch (IOException e) {
            ModLogger.error("Could not write default enchant shop file", e);
        }
    }

    /** Drops offers whose price is below the value the enchant adds, once prices are known. */
    public void retain(java.util.function.Predicate<Offer> valid) {
        offers.values().removeIf(offer -> !valid.test(offer));
    }
    public void priceOffers(java.util.function.ToLongFunction<Offer> pricing) {
        offers.replaceAll((key, offer) -> new Offer(offer.id(), offer.enchantmentId(), offer.level(),
                pricing.applyAsLong(offer), offer.stage()));
    }

    void validatePrices(java.util.function.Function<String, Long> addedValue) {
        if (addedValue == null) {
            return;
        }
        List<Offer> safe = new ArrayList<>();
        for (Offer offer : offers.values()) {
            Long value = addedValue.apply(offer.enchantmentId());
            if (value != null && value > 0 && offer.price() < value) {
                ModLogger.warn("Enchant offer '{}' rejected: price {} is below the value it adds {}",
                        offer.id(), offer.price(), value);
                continue;
            }
            safe.add(offer);
        }
        offers.clear();
        for (Offer offer : safe) {
            offers.put(offer.id(), offer);
        }
    }
}
