package com.evolt.teamecon.shop;

import com.evolt.teamecon.gambling.CasinoProgression;
import com.evolt.teamecon.gambling.HiLoRules;
import com.evolt.teamecon.market.DemandState;
import com.evolt.teamecon.market.MarketParams;
import com.evolt.teamecon.price.BasePrices;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.price.TradePolicy;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseBalanceTest {
    @TempDir Path dir;

    @Test void tenSidedRollPartitionsEveryOutcomeExactlyOnce() {
        int high=0,low=0;
        for(int roll=1;roll<=10;roll++) {
            boolean h=HiLoRules.wins(roll,"high"),l=HiLoRules.wins(roll,"low");
            assertNotEquals(h,l);if(h)high++;if(l)low++;
        }
        assertEquals(5,high);assertEquals(5,low);
        assertFalse(HiLoRules.wins(0,"low"));assertFalse(HiLoRules.wins(11,"high"));
        assertFalse(HiLoRules.wins(4,"bogus"));
        assertEquals(.9, new CasinoProgression().profile("hilo").payoutScale(),1e-12);
    }

    @Test void oldHiloRewardMigratesOnceAndExplicitNewOverridesRemain() {
        var json=new CasinoProgression().toJson();json.addProperty("version",3);
        var hilo=json.getAsJsonObject("games").getAsJsonObject("hilo");
        hilo.addProperty("payoutScale",.98);hilo.addProperty("maxBet",123);
        var migrated=CasinoProgression.fromJson(json);
        assertEquals(.90,migrated.profile("hilo").payoutScale());assertEquals(123,migrated.profile("hilo").maxBet());
        hilo.addProperty("payoutScale",.87);assertEquals(.87,CasinoProgression.fromJson(json).profile("hilo").payoutScale());
        json.addProperty("version",4);hilo.addProperty("payoutScale",.98);
        assertEquals(.98,CasinoProgression.fromJson(json).profile("hilo").payoutScale());
    }

    @Test void foodRetailPreservesAnIncentiveToFarmWithoutInflatingResale() {
        var base=new BasePrices();base.load(dir.resolve("base.json"));var retail=new ShopPricing();
        assertEquals(2,base.get("minecraft:potato"));
        assertEquals(8,retail.itemPrice("minecraft:potato",4,256));
        assertEquals(16,retail.itemPrice("minecraft:beef",2,256));
        assertEquals(24,retail.itemPrice("minecraft:bread",14,256));
        assertEquals(32,retail.itemPrice("minecraft:cooked_beef",4,256));
        assertEquals(6,200/retail.itemPrice("minecraft:cooked_beef",4,256));
        assertEquals(1_000,retail.itemPrice("minecraft:cooked_beef",1_000,256));
    }

    @Test void defaultBoxesHaveNoFillerAndCoverRetailWithoutResaleProfit() {
        var pools=new BlindBoxPools();pools.load(dir,null);
        var prices=new PriceService();prices.basePrices().load(dir.resolve("base.json"));var retail=new ShopPricing();
        for(var pool:pools.all()) {
            assertEquals(10_000,pool.totalWeight());
            for(var entry:pool.entries()) {
                assertNotEquals("minecraft:air",entry.itemKey());assertNotEquals("minecraft:rotten_flesh",entry.itemKey());
                if(!BoxPrizePolicy.exclusive(entry.itemKey())) {
                    long unit=prices.basePrices().get(entry.itemKey());assertTrue(unit>0);
                    assertTrue(retail.itemPrice(entry.itemKey(),unit*2,256)*entry.count()>=pool.price());
                }
            }
            double ev=BlindBoxPools.expectedValue(pool,prices);
            assertTrue(ev>pool.price()*.4&&ev<pool.price());
        }
        assertTrue(BlindBoxPools.expectedValue(pools.byId("common"),prices)>32.64);
        assertTrue(BlindBoxPools.expectedValue(pools.byId("rare"),prices)>248.2176);
        var eggs=pools.byId("rare").entries().stream().filter(e->BoxPrizePolicy.exclusive(e.itemKey())).toList();
        assertEquals(BoxPrizePolicy.EGGS.size(),eggs.size());
        assertEquals(.1,eggs.stream().mapToInt(ShopPool.Entry::weight).sum()/10000D,1e-12);
    }

    @Test void livestockRewardsCannotOpenOrdinaryTradingOrDangerousEggs() {
        for(String id:BoxPrizePolicy.EGGS) {
            assertTrue(BoxPrizePolicy.allowed(id));assertFalse(TradePolicy.canTrade(id));assertFalse(TradePolicy.canSell(id));
        }
        for(String id:new String[]{"minecraft:zombie_spawn_egg","minecraft:blaze_spawn_egg","minecraft:warden_spawn_egg","minecraft:spawner","example:cow_spawn_egg"})
            assertFalse(BoxPrizePolicy.allowed(id));
    }

    @Test void prizeOnlyEggsDoNotLockTheTreasureBoxAtThePurchaseGate() {
        var pools = new BlindBoxPools();
        pools.load(dir, null);
        var shop = new ShopService(null, null, null);
        assertTrue(shop.boxAccess(null, pools.byId("rare")).unlocked(),
                "The shipped treasure pool must remain purchasable with prize-only eggs");
        for (String id : new String[]{"minecraft:zombie_spawn_egg", "minecraft:spawner", "example:cow_spawn_egg"}) {
            var json = com.google.gson.JsonParser.parseString("""
                    {"id":"invalid","price":512,"stage":"","entries":[{"item":"%s","count":1,"weight":1}]}
                    """.formatted(id)).getAsJsonObject();
            var pool = new ShopPool();
            assertTrue(pool.read(json));
            assertFalse(shop.boxAccess(null, pool).unlocked(), id);
        }
    }

    @Test void legacyPoolsAreBackedUpButCustomRewardsArePreserved() throws Exception {
        var file=dir.resolve("teamecon_blindbox.json");var legacy=BlindBoxPools.legacyDefaults();
        String original=new GsonBuilder().setPrettyPrinting().create().toJson(legacy);Files.writeString(file,original);
        var pools=new BlindBoxPools();pools.load(dir,null);
        assertEquals(original,Files.readString(dir.resolve("teamecon_blindbox.json.pre-1.0.bak")));
        assertEquals(10000,pools.byId("rare").totalWeight());
        legacy.get(0).getAsJsonObject().addProperty("price",99);String custom=legacy.toString();Files.writeString(file,custom);
        pools.load(dir,null);assertEquals(99,pools.byId("common").price());assertEquals(custom,Files.readString(file));
    }

    @Test void firstUpgradeIsReachableFromAnOrdinaryMixedMiningBasket() {
        var demand=new DemandState(new MarketParams(.01,.05,30,0,true));
        var basket=Map.of("iron",new int[]{32,8},"copper",new int[]{32,4},"gold",new int[]{8,64},
                "coal",new int[]{64,2},"logs",new int[]{64,2},"wheat",new int[]{64,2},"cobble",new int[]{64,1});
        double total=0;
        for(var row:basket.entrySet()) total+=row.getValue()[1]*demand.factorIntegral(DemandState.SERVER_SCOPE,row.getKey(),row.getValue()[0],0);
        assertEquals(1150.75,total,.01);assertTrue(200+(long)total>=new CasinoProgression().levelCost(2));
        // The nonzero demand floor deliberately permits continuing farm income; it is not a hard inflation cap.
        demand.addDip(DemandState.SERVER_SCOPE,"gold",10_000,0);
        assertEquals(81_920,64*demand.factorIntegral(DemandState.SERVER_SCOPE,"gold",25_600,0),1e-6);
    }

    @Test void releasedBoxDefaultsMigrateOnceWithAnExactBackup() throws Exception {
        var file=dir.resolve("teamecon_blindbox.json");
        String previous=BlindBoxPools.previousDefaults().toString();
        Files.writeString(file,previous);
        var pools=new BlindBoxPools();pools.load(dir,null);
        assertEquals(previous,Files.readString(dir.resolve("teamecon_blindbox.json.pre-expanded.bak")));
        assertEquals(54,pools.byId("rare").entries().size());
        String updated=Files.readString(file);pools.load(dir,null);
        assertEquals(updated,Files.readString(file));
    }
}
