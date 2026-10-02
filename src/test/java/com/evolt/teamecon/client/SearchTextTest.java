package com.evolt.teamecon.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchTextTest {
    @Test void simplifiedChineseMatchesFullPinyinAndInitials() {
        var gold = SearchText.of("金锭", "minecraft:gold_ingot");
        for (String query : new String[]{"金锭", "jinding", "JIN DING", "jīn-dìng", "jd", "gold_ingot", "minecraft:gold"})
            assertTrue(gold.matches(query), query);
        assertFalse(gold.matches("jinkuai"));
        assertFalse(gold.matches("   !!!"));
    }

    @Test void traditionalChineseAndUmlautInputWork() {
        assertTrue(SearchText.of("鑽石", "minecraft:diamond").matches("zuanshi"));
        var emerald = SearchText.of("绿宝石", "minecraft:emerald");
        for (String query : new String[]{"lübaoshi", "lvbaoshi", "lu:baoshi", "lbs"})
            assertTrue(emerald.matches(query), query);
    }

    @Test void customNamesAndIndependentKeysDoNotCrossMatch() {
        assertTrue(SearchText.of("自定义铜齿轮", "example:copper_gear").matches("zdytcl"));
        var english = SearchText.of("Iron Ingot", "minecraft:iron_ingot");
        assertTrue(english.matches("IRON"));
        assertTrue(english.matches("minecraft:iron"));
        assertTrue(english.matches(""));
        assertFalse(english.matches("ingotminecraft"));
        assertTrue(SearchText.of("自动售货机", "teamecon:shop_machine").matches("zdshj"));
    }
}
