package com.evolt.teamecon.price;

import com.evolt.teamecon.economy.MoneyMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.ConcurrentModificationException;
import static org.junit.jupiter.api.Assertions.*;

class PriceOverridesTest {
    @TempDir Path dir;
    @Test void independentDirectionsPersistAndResetWithoutTouchingOtherItems() throws Exception {
        var prices = new PriceOverrides(); prices.load(dir);
        prices.save("minecraft:gold_ingot", new PriceOverrides.Entry(200, 0), prices.revision());
        prices.save("example:gear", new PriceOverrides.Entry(-1, 70), prices.revision());
        var fresh = new PriceOverrides(); fresh.load(dir);
        assertTrue(fresh.valid());
        assertEquals(new PriceOverrides.Entry(200, 0), fresh.get("minecraft:gold_ingot"));
        assertEquals(new PriceOverrides.Entry(-1, 70), fresh.get("example:gear"));
        fresh.save("minecraft:gold_ingot", PriceOverrides.DEFAULT, fresh.revision());
        prices.load(dir);
        assertEquals(PriceOverrides.DEFAULT, prices.get("minecraft:gold_ingot"));
        assertEquals(70, prices.get("example:gear").sell());
        assertTrue(Files.exists(dir.resolve(PriceOverrides.FILE_NAME + ".bak")));
    }
    @Test void staleAdminCannotOverwriteAnAcceptedChange() throws Exception {
        var prices = new PriceOverrides(); prices.load(dir); long old = prices.revision();
        prices.save("minecraft:apple", new PriceOverrides.Entry(50, 10), old);
        String accepted = Files.readString(dir.resolve(PriceOverrides.FILE_NAME));
        assertThrows(ConcurrentModificationException.class, () -> prices.save("minecraft:apple", new PriceOverrides.Entry(1, 1), old));
        assertEquals(accepted, Files.readString(dir.resolve(PriceOverrides.FILE_NAME)));
        assertEquals(50, prices.get("minecraft:apple").buy());
    }
    @Test void externalFileEditsArePreservedUntilReload() throws Exception {
        var prices = new PriceOverrides(); prices.load(dir);
        String external = "{\"version\":1,\"items\":{\"example:gear\":{\"sell\":99}}}";
        Files.writeString(dir.resolve(PriceOverrides.FILE_NAME), external);
        assertThrows(IOException.class, () -> prices.save("minecraft:apple", new PriceOverrides.Entry(50, 10), prices.revision()));
        assertEquals(external, Files.readString(dir.resolve(PriceOverrides.FILE_NAME)));
        assertEquals(PriceOverrides.DEFAULT, prices.get("minecraft:apple"));
        prices.load(dir); assertEquals(99, prices.get("example:gear").sell());
    }
    @Test void invalidConfigClosesTradingAndIsNeverRewrittenBySave() throws Exception {
        for (String value : new String[]{"1.5", "\"20\"", "-2", "1000000001", "null"}) {
            String json = "{\"version\":1,\"items\":{\"minecraft:apple\":{\"buy\":" + value + "}}}";
            Files.writeString(dir.resolve(PriceOverrides.FILE_NAME), json);
            var prices = new PriceOverrides(); prices.load(dir);
            assertFalse(prices.valid(), value);
            assertThrows(IOException.class, () -> prices.save("minecraft:apple", PriceOverrides.DEFAULT, prices.revision()));
            assertEquals(json, Files.readString(dir.resolve(PriceOverrides.FILE_NAME)));
        }
    }
    @Test void boundedOverridesCannotEnableRestrictedItemsOrBlankTickets() {
        assertThrows(IllegalArgumentException.class, () -> new PriceOverrides.Entry(MoneyMath.MAX_PRICE + 1, 0));
        for (String id : new String[]{"minecraft:command_block", "minecraft:pig_spawn_egg", "teamecon:scratch_card_copper", "minecraft:enchanted_book", "minecraft:bad id"})
            assertThrows(IllegalArgumentException.class, () -> PriceOverrides.validate(id, new PriceOverrides.Entry(20, 10)), id);
        assertThrows(IllegalArgumentException.class, () -> PriceOverrides.validate("teamecon:shop_machine", new PriceOverrides.Entry(-1, 20)));
        assertDoesNotThrow(() -> PriceOverrides.validate("example:gear", new PriceOverrides.Entry(200, 20)));
    }
}
