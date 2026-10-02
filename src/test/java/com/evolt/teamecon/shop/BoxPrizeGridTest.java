package com.evolt.teamecon.shop;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxPrizeGridTest {
    private JsonArray entries(String json){return JsonParser.parseString(json).getAsJsonArray();}
    @Test void oldPoolsKeepOrderAndEmptyCellsNeverBecomeBlankPrizes(){
        var grid=new BoxPrizeGrid(entries("""
            [{"item":"minecraft:diamond","count":8,"weight":3,"note":"owner"},
             {"item":"minecraft:potion","potion":"minecraft:healing","count":1,"weight":1}]
            """));
        grid.swap(0,53);var saved=grid.entries();assertEquals(2,saved.size());
        var loaded=new BoxPrizeGrid(saved);assertNull(loaded.get(0));assertEquals("owner",loaded.get(53).get("note").getAsString());
        assertEquals(3,loaded.get(53).get("weight").getAsInt());assertEquals("minecraft:healing",loaded.get(1).get("potion").getAsString());
        loaded.swap(53,1);assertEquals(8,loaded.get(1).get("count").getAsInt());
        loaded.set(53,null);assertEquals(1,loaded.entries().size());
    }
    @Test void pagesAndExplicitPositionsRoundTripWithoutCollidingWithLegacyEntries(){
        var grid=new BoxPrizeGrid(entries("""
            [{"item":"minecraft:diamond","count":1,"weight":1},
             {"item":"minecraft:iron_ingot","count":16,"weight":3,"slot":0},
             {"item":"minecraft:gold_ingot","count":4,"weight":2,"slot":127}]
            """));
        assertEquals("minecraft:diamond",grid.get(1).get("item").getAsString());assertEquals(2,grid.firstEmpty());
        assertEquals(grid.entries(),new BoxPrizeGrid(grid.entries()).entries());
    }
    @Test void invalidOrDuplicatePositionsCannotOverwritePrizes(){
        assertThrows(IllegalArgumentException.class,()->new BoxPrizeGrid(entries("[{\"slot\":128}]")));
        assertThrows(IllegalArgumentException.class,()->new BoxPrizeGrid(entries("[{\"slot\":-1}]")));
        assertThrows(IllegalArgumentException.class,()->new BoxPrizeGrid(entries("[{\"slot\":1},{\"slot\":1}]")));
    }
}
