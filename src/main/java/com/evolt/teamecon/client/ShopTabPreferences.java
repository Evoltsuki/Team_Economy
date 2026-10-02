package com.evolt.teamecon.client;

import com.evolt.teamecon.util.ModLogger;
import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/** Per-player local navigation preferences, independent of server pricing configuration. */
public final class ShopTabPreferences {
    public static final String FILE_NAME = "teamecon_client_preferences.json";
    private static final Set<String> TABS = Set.of("items", "sell", "machines", "cards", "levels");
    private ShopTabPreferences() { }
    private static JsonObject read(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        if (Files.size(file) > 1_000_000) throw new IOException("Preference file too large");
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
    public static String last(Path directory, UUID player) {
        try {
            JsonObject tabs = read(directory.resolve(FILE_NAME)).getAsJsonObject("shopTabs");
            String tab = tabs != null && tabs.has(player.toString()) ? tabs.get(player.toString()).getAsString() : "items";
            return TABS.contains(tab) ? tab : "items";
        } catch (IOException | RuntimeException ex) {
            ModLogger.warn("Could not read shop page preference: {}", ex.toString()); return "items";
        }
    }
    public static void remember(Path directory, UUID player, String tab) {
        if (!TABS.contains(tab)) return;
        Path temporary = null;
        try {
            Path file = directory.resolve(FILE_NAME);
            JsonObject root = read(file), tabs = root.getAsJsonObject("shopTabs");
            if (tabs == null) { tabs = new JsonObject(); root.add("shopTabs", tabs); }
            tabs.addProperty(player.toString(), tab);
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "teamecon-pages-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException | RuntimeException ex) { ModLogger.warn("Could not save shop page preference: {}", ex.toString()); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }
}
