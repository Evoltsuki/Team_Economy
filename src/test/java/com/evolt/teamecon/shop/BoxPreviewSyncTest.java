package com.evolt.teamecon.shop;

import com.evolt.teamecon.client.ClientShopCache;
import com.evolt.teamecon.network.payloads.ShopSyncPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxPreviewSyncTest {
    private ShopSyncPayload packet(int part,String boxes,String rewards) {
        return new ShopSyncPayload(7,1000,"Team",0,part,2,"",boxes,"","","",rewards);
    }
    @Test void poolSplitAcrossPacketsRetainsEveryPrizeAndReceiptUpdatesDoNotErasePreview() {
        ClientShopCache.clear();
        ClientShopCache.update(packet(0,"rare,512,,1,minecraft:diamond,1,9999,,",""));
        ClientShopCache.update(packet(1,"rare,512,,1,minecraft:cow_spawn_egg,1,1,,",""));
        assertEquals(1,ClientShopCache.boxes().size());assertEquals(2,ClientShopCache.boxes().getFirst().prizes().size());
        ClientShopCache.update(packet(-1,"","minecraft:cow_spawn_egg,1,"));
        assertEquals(2,ClientShopCache.boxes().getFirst().prizes().size());
        assertEquals("minecraft:cow_spawn_egg",ClientShopCache.rewards().getFirst().itemKey());
        ClientShopCache.update(packet(0,"common,64,,1,minecraft:iron_ingot,4,10000,,",""));
        assertEquals(1,ClientShopCache.boxes().size());assertTrue(ClientShopCache.rewards().isEmpty());
        ClientShopCache.clear();
    }
    @Test void duplicatePrizesMergeAcrossChunksButPotionVariantsKeepSeparateOdds() {
        ClientShopCache.clear();
        ClientShopCache.update(packet(0,"alchemy,120,,1,minecraft:potion,1,3,minecraft:healing,炼金盲盒",""));
        ClientShopCache.update(packet(1,"alchemy,120,,1,minecraft:potion,1,2,minecraft:healing,炼金盲盒;alchemy,120,,1,minecraft:potion,1,5,minecraft:swiftness,炼金盲盒",""));
        var pool=ClientShopCache.boxes().getFirst();
        assertEquals("炼金盲盒",pool.name());assertEquals(120,pool.price());assertEquals(2,pool.prizes().size());
        assertEquals(10,pool.prizes().stream().mapToInt(ClientShopCache.BoxPrize::weight).sum());
        assertEquals(5,pool.prizes().getFirst().weight());assertEquals("minecraft:healing",pool.prizes().getFirst().potion());
        ClientShopCache.clear();
    }
}
