package com.evolt.teamecon.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ShopTabPreferencesTest {
    @TempDir Path dir;
    @Test void pagesPersistOnDiskSeparatelyForEachPlayer() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals("items", ShopTabPreferences.last(dir, first));
        ShopTabPreferences.remember(dir, first, "sell");
        ShopTabPreferences.remember(dir, second, "levels");
        assertEquals("sell", ShopTabPreferences.last(dir, first));
        assertEquals("levels", ShopTabPreferences.last(dir, second));
        ShopTabPreferences.remember(dir, first, "cards");
        assertEquals("cards", ShopTabPreferences.last(dir, first));
        assertEquals("levels", ShopTabPreferences.last(dir, second));
        ShopTabPreferences.remember(dir, first, "boxes");
        assertEquals("cards", ShopTabPreferences.last(dir, first));
    }
    @Test void malformedPreferencesFallBackWithoutOverwritingTheFile() throws Exception {
        Path file = dir.resolve(ShopTabPreferences.FILE_NAME); Files.writeString(file, "broken-json");
        UUID player = UUID.randomUUID();
        assertEquals("items", ShopTabPreferences.last(dir, player));
        ShopTabPreferences.remember(dir, player, "sell");
        assertEquals("broken-json", Files.readString(file));
    }
}
