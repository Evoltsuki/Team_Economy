package com.evolt.teamecon.price;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialGroupsTest {
    @Test void minecraftNamespacesAndSpecialBlockNamesResolveCorrectly() {
        MaterialGroups groups=new MaterialGroups();
        assertEquals("minecraft:iron_ingot",groups.groupFor("minecraft:iron_block"));
        assertEquals(9,groups.unitsFor("minecraft:iron_block",1),1e-12);
        assertEquals(1,groups.unitsFor("minecraft:gold_nugget",9),1e-12);
        assertEquals("minecraft:lapis_lazuli",groups.groupFor("minecraft:lapis_block"));
    }
    @Test void operatorItemsCannotEnterTheSurvivalShop() {
        assertFalse(TradePolicy.canTrade("minecraft:command_block"));
        assertFalse(TradePolicy.canTrade("minecraft:creeper_spawn_egg"));
        assertTrue(TradePolicy.canTrade("minecraft:nether_star"));
        assertTrue(TradePolicy.canTrade("teamecon:penguin_machine"));
    }
}
