package com.evolt.teamecon.shop;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerCatalogTest {
    @TempDir Path dir;
    private ShopCatalog load(String json) throws Exception {
        Files.writeString(dir.resolve(ShopCatalog.FILE_NAME), json);
        ShopCatalog catalog = new ShopCatalog(); catalog.load(dir, id -> true); return catalog;
    }

    @Test void customOnlyCatalogueAndExclusionsApplyToExplicitOffers() throws Exception {
        var c = load("""
                {"version":1,"includeDefaultItems":false,"disabledItems":["minecraft:diamond"],"items":[
                 {"item":"minecraft:bread","price":40}, {"item":"minecraft:diamond","price":1000},
                 {"item":"example:circuit","price":600,"stage":"industry"}]}
                """);
        assertTrue(c.valid()); assertTrue(c.allows("minecraft:bread")); assertTrue(c.allows("example:circuit"));
        assertFalse(c.allows("minecraft:diamond")); assertFalse(c.allows("minecraft:dirt"));
        assertEquals("industry", c.stage("example:circuit"));
        assertEquals(40, c.price("minecraft:bread", 999, 14));
        assertEquals(60, c.price("minecraft:bread", 999, 60));
    }

    @Test void malformedPricesAndDuplicatesCloseTheCatalogueWithoutRewritingFiles() throws Exception {
        for (String entries : new String[]{
                "{\"item\":\"minecraft:bread\",\"price\":1.5}",
                "{\"item\":\"minecraft:bread\",\"price\":\"40\"}",
                "{\"item\":\"minecraft:bread\",\"price\":0}",
                "{\"item\":\"minecraft:bread\",\"price\":20},{\"item\":\"minecraft:bread\",\"price\":40}",
                "{\"item\":\"teamecon:scratch_card_match\",\"price\":20}",
                "{\"item\":\"minecraft:command_block\",\"price\":20}"}) {
            String json = "{\"version\":1,\"items\":[" + entries + "]}";
            var c = load(json); assertFalse(c.valid()); assertFalse(c.allows("minecraft:dirt"));
            assertEquals(json, Files.readString(dir.resolve(ShopCatalog.FILE_NAME)));
        }
    }

    @Test void reloadRemovesDeletedCustomOffersAndRestoresDefaultMode() throws Exception {
        var c = load("{\"version\":1,\"includeDefaultItems\":false,\"items\":[{\"item\":\"example:circuit\",\"price\":50}]}");
        Files.writeString(dir.resolve(ShopCatalog.FILE_NAME), "{\"version\":1,\"items\":[]}");
        c.load(dir, id -> true);
        assertFalse(c.allows("example:circuit")); assertTrue(c.allows("minecraft:bread"));
    }

    @Test void poolRejectsFractionalOrInvalidRewardsInsteadOfChangingOdds() {
        for (String invalid : new String[]{"0", "-1", "1.5", "1000001", "2147483649", "\"3\""}) {
            var pool = new ShopPool();
            assertThrows(RuntimeException.class, () -> pool.read(JsonParser.parseString("""
                    {"id":"custom","price":50,"entries":[
                    {"item":"minecraft:bread","weight":1},{"item":"minecraft:diamond","weight":%s}]}
                    """.formatted(invalid)).getAsJsonObject()));
            assertFalse(pool.ready());
        }
    }

    @Test void modernPoolsAllowExplicitOptInAndDisabledPoolsStayAbsent() throws Exception {
        String json = """
                {"version":1,"pools":[
                  {"id":"modded","price":50,"allowModdedItems":true,"enforceValueCap":false,
                   "entries":[{"item":"example:circuit","weight":3},{"item":"minecraft:bread","weight":1}]},
                  {"id":"disabled","price":50,"enabled":false,"entries":[{"item":"minecraft:bread"}]}]}
                """;
        Files.writeString(dir.resolve("teamecon_blindbox.json"), json);
        var pools = new BlindBoxPools(); pools.load(dir, null);
        assertEquals(1, pools.all().size()); assertNull(pools.byId("disabled"));
        var pool = pools.byId("modded"); assertTrue(pool.allows("example:circuit"));
        assertFalse(pool.allows("teamecon:scratch_card_match")); assertFalse(pool.enforceValueCap());
        assertEquals(4, pool.totalWeight()); assertEquals(json, Files.readString(dir.resolve("teamecon_blindbox.json")));
    }

    @Test void duplicateBoxIdsDisableAmbiguousConfiguration() throws Exception {
        String box = "{\"id\":\"same\",\"price\":50,\"entries\":[{\"item\":\"minecraft:bread\"}]}";
        Files.writeString(dir.resolve("teamecon_blindbox.json"), "[" + box + "," + box + "]");
        var pools = new BlindBoxPools(); pools.load(dir, null); assertFalse(pools.ready());
    }
}
