package com.evolt.teamecon.shop;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class BoxChancesTest {
    @Test void editedChanceKeepsItsValueAndPreservesOtherRatios(){
        var rows=entries("[{\"slot\":0,\"chance\":50},{\"slot\":12,\"chance\":30},{\"slot\":90,\"chance\":20}]");
        var balanced=BoxChances.rebalance(rows,0,25_000_000);
        assertArrayEquals(new int[]{25_000_000,45_000_000,30_000_000},BoxChances.weights(balanced));
        assertEquals(50_000_000,BoxChances.entryUnits(rows.get(0).getAsJsonObject()));
        assertEquals(90,balanced.get(2).getAsJsonObject().get("slot").getAsInt());
    }
    @Test void additionsRemovalAndRoundingStayAtOneHundredPercent(){
        var rows=entries("[{\"slot\":0,\"chance\":100},{\"slot\":1,\"chance\":50}]");
        var balanced=BoxChances.rebalance(rows,1,50_000_000);
        assertArrayEquals(new int[]{50_000_000,50_000_000},BoxChances.weights(balanced));
        balanced.remove(0);
        assertArrayEquals(new int[]{100_000_000},BoxChances.weights(BoxChances.rebalance(balanced,-1,0)));
        var thirds=entries("[{\"slot\":1,\"chance\":1},{\"slot\":2,\"chance\":1},{\"slot\":3,\"chance\":1}]");
        assertArrayEquals(new int[]{33_333_334,33_333_333,33_333_333},BoxChances.weights(BoxChances.rebalance(thirds,-1,0)));
    }
    @Test void changingAOneHundredPercentPrizeCanReviveTheOtherPrizes(){
        var rows=entries("[{\"slot\":0,\"chance\":100},{\"slot\":1,\"chance\":0},{\"slot\":2,\"chance\":0}]");
        assertArrayEquals(new int[]{50_000_000,25_000_000,25_000_000},BoxChances.weights(BoxChances.rebalance(rows,0,50_000_000)));
        assertArrayEquals(new int[]{0,100_000_000,0},BoxChances.weights(BoxChances.rebalance(rows,1,100_000_000)));
        assertTrue(BoxChances.rebalance(new JsonArray(),-1,0).isEmpty());
    }
    @TempDir Path dir;
    private JsonArray entries(String json){return JsonParser.parseString(json).getAsJsonArray();}
    private JsonObject pool(JsonArray entries){
        var pool=new JsonObject();pool.addProperty("id","custom");pool.addProperty("price",10);pool.add("entries",entries);return pool;
    }
    @Test void decimalInputIsExactAndSupportsPercentSuffix(){
        assertEquals(12_500_000,BoxChances.parse("12.5%"));
        assertEquals(500_000,BoxChances.parse(" .5 ％ "));
        assertEquals(1,BoxChances.parse("0.000001"));
        assertEquals(BoxChances.TOTAL,BoxChances.parse("100"));
        assertEquals("33.333333",BoxChances.format(33_333_333));
        for(String invalid:new String[]{"", ".", "-1", "100.000001", "0.0000001", "NaN", "Infinity", "1e2", "10%%"})
            assertThrows(RuntimeException.class,()->BoxChances.parse(invalid),invalid);
    }
    @Test void decimalTotalsUseExactTicketsRatherThanFloatingPointTolerance(){
        var rows=entries("""
            [{"chance":33.333333},{"chance":33.333333},{"chance":33.333334}]
            """);
        assertArrayEquals(new int[]{33_333_333,33_333_333,33_333_334},BoxChances.weights(rows));
        rows.get(2).getAsJsonObject().addProperty("chance",33.333333);
        assertThrows(IllegalArgumentException.class,()->BoxChances.weights(rows));
        rows.get(2).getAsJsonObject().addProperty("chance",33.333335);
        assertThrows(IllegalArgumentException.class,()->BoxChances.weights(rows));
    }
    @Test void malformedOrMixedPercentageRowsRejectTheWholePool(){
        for(String json:new String[]{"[{\"chance\":0}]","[{\"chance\":99.9}]","[{\"chance\":100.1}]",
                "[{\"chance\":\"100\"}]","[{\"chance\":true}]","[{\"chance\":null}]",
                "[{\"chance\":99.9999999},{\"chance\":0.0000001}]",
                "[{\"chance\":100,\"weight\":1}]","[{\"chance\":75},{\"weight\":25}]"})
            assertThrows(RuntimeException.class,()->BoxChances.weights(entries(json)),json);
    }
    @Test void zeroPercentNeverWinsAndOneHundredAlwaysWins(){
        var pool=new ShopPool();pool.read(pool(entries("""
            [{"item":"minecraft:air","chance":0},{"item":"minecraft:bread","chance":100}]
            """)));
        var random=net.minecraft.util.RandomSource.create(123);
        for(int i=0;i<1000;i++)assertEquals("minecraft:bread",pool.draw(random).itemKey());
        assertEquals(BoxChances.TOTAL,pool.totalWeight());
    }
    @Test void percentageDrawHitsExactBoundaryBetweenPrizes(){
        var pool=new ShopPool();pool.read(pool(entries("""
            [{"item":"minecraft:air","chance":12.5},{"item":"minecraft:bread","chance":87.5}]
            """)));
        var random=new net.minecraft.world.level.levelgen.LegacyRandomSource(1){
            private int roll=12_499_999;
            @Override public int nextInt(int bound){assertEquals(100_000_000,bound);return roll++;}
        };
        assertEquals("minecraft:air",pool.draw(random).itemKey());
        assertEquals("minecraft:bread",pool.draw(random).itemKey());
    }
    @Test void legacyConversionPreservesMetadataAndOnlyChangesTheReturnedCopy(){
        var rows=entries("""
            [{"item":"minecraft:air","weight":1,"slot":12,"note":"keep"},
             {"item":"minecraft:air","weight":1,"slot":0},
             {"item":"minecraft:air","weight":1,"slot":80}]
            """);
        String original=rows.toString();var converted=BoxChances.asPercentages(rows);
        assertEquals(original,rows.toString());assertEquals(BoxChances.TOTAL,BoxChances.totalUnits(converted));
        assertArrayEquals(new int[]{33_333_334,33_333_333,33_333_333},BoxChances.weights(converted));
        assertEquals("keep",converted.get(0).getAsJsonObject().get("note").getAsString());
        assertEquals(12,converted.get(0).getAsJsonObject().get("slot").getAsInt());
        assertFalse(converted.get(0).getAsJsonObject().has("weight"));
    }
    @Test void conversionKeepsExtremelyRareLegacyRewardsReachable(){
        var rows=new JsonArray();
        for(int i=0;i<128;i++){var row=new JsonObject();row.addProperty("weight",i<2?1:1_000_000);rows.add(row);}
        int[] converted=BoxChances.weights(BoxChances.asPercentages(rows));
        assertTrue(Arrays.stream(converted).allMatch(n->n>0));assertEquals(BoxChances.TOTAL,Arrays.stream(converted).sum());
    }
    @Test void incompleteDraftIsNotNormalizedAndOtherProbabilitiesStayFixed(){
        var rows=entries("[{\"chance\":12.5},{\"chance\":25}]");
        assertEquals(37_500_000,BoxChances.totalUnits(rows));
        assertEquals(rows,BoxChances.asPercentages(rows));
        assertThrows(IllegalArgumentException.class,()->BoxChances.weights(rows));
    }
    @Test void persistenceRejectsInvalidTotalsWithoutChangingFileOrRevision()throws Exception{
        Path file=dir.resolve("teamecon_blindbox.json");Files.writeString(file,"{\"version\":1,\"pools\":[]}");
        var config=new BoxAdminConfig();config.load(dir);long revision=config.revision();String original=Files.readString(file);
        var draft=pool(entries("[{\"item\":\"minecraft:air\",\"chance\":12.5}]"));
        assertThrows(IllegalArgumentException.class,()->config.save("",draft,false,revision,null));
        assertEquals(revision,config.revision());assertEquals(original,Files.readString(file));
        draft.getAsJsonArray("entries").add(entries("[{\"item\":\"minecraft:air\",\"chance\":87.5}]").get(0));
        config.save("",draft,false,revision,null);var reloaded=new BoxAdminConfig();reloaded.load(dir);
        assertTrue(reloaded.ready());assertEquals(draft,reloaded.pool("custom"));
    }
    @Test void changingOnlyHeadersPreservesLegacyWeightsExactly()throws Exception{
        Path file=dir.resolve("teamecon_blindbox.json");
        var original=entries("[{\"item\":\"minecraft:air\",\"weight\":1},{\"item\":\"minecraft:air\",\"weight\":999999}]");
        Files.writeString(file,"{\"version\":1,\"pools\":["+pool(original)+"]}");
        var config=new BoxAdminConfig();config.load(dir);var draft=config.pool("custom");draft.addProperty("price",40);
        config.save("custom",draft,false,config.revision(),null);
        assertEquals(original,config.pool("custom").getAsJsonArray("entries"));
    }
}
