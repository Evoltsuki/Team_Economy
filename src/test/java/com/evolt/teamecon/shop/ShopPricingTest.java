package com.evolt.teamecon.shop;
import com.evolt.teamecon.gambling.CasinoProgression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ShopPricingTest {
    @TempDir Path config;
    @Test void legacyCheapMendingAndRareCraftedGoodsReceiveRetailFloors() {
        var prices=new ShopPricing();
        assertEquals(65536,prices.enchantPrice("minecraft:mending",1,100,256));
        assertEquals(256,prices.enchantPrice("minecraft:mending",1,100,256)/256);
        assertEquals(8192,prices.itemPrice("minecraft:red_shulker_box",600,256));
        assertEquals(24576,prices.itemPrice("minecraft:lodestone",1000,256));
        assertEquals(120000,prices.enchantPrice("minecraft:mending",1,120000,256));
        assertEquals(3,prices.itemPrice("minecraft:cobblestone",3,256));
    }
    @Test void increasingDiamondSalePricesCannotMakeHighEndBooksCheaperInDiamondTerms() {
        var prices=new ShopPricing();
        assertEquals(262144,prices.enchantPrice("minecraft:mending",1,100,1024));
        assertEquals(65536,prices.enchantPrice("minecraft:mending",1,100,64));
    }
    @Test void invalidPriceFileRetainsSafeDefaultsAndExplicitOverridesWork() throws Exception {
        Files.writeString(config.resolve(ShopPricing.FILE_NAME),"{broken");
        var prices=new ShopPricing();prices.load(config);
        assertEquals(65536,prices.enchantPrice("minecraft:mending",1,1,256));
        Files.writeString(config.resolve(ShopPricing.FILE_NAME),"""
                {"version":1,"enabled":true,"minimumItemPrices":{},"minimumEnchantmentPrices":{"minecraft:mending":90000}}
                """);
        prices.load(config);assertEquals(90000,prices.enchantPrice("minecraft:mending",1,1,256));
    }
}
