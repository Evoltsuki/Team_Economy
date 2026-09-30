package com.evolt.teamecon.gambling;

import com.evolt.teamecon.price.TradePolicy;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class RevisionMathTest {
    @Test void laterHopsAlwaysBecomeHarderEvenAtShortenedCaps() {
        for(double cap:new double[]{2,5,16,40,1000,1_000_000}) {
            double current=1,chance=1;
            for(int layer=0;current<cap&&layer<100;layer++) {
                var jump=PenguinGame.next(layer,current,cap,1);
                assertTrue(jump.successChance()<chance,"Chance rose at layer "+layer+" / cap "+cap);
                assertTrue(jump.expectedFactor()<=.96+1e-12);
                chance=jump.successChance();current=jump.applySuccess(current);
            }
            assertEquals(cap,current,1e-7);
        }
    }
    @Test void crashAcceleratesAndHasARareButReachableHundredfoldTail() {
        double early=CrashGame.multiplier(100,1000)-CrashGame.multiplier(80,1000);
        double late=CrashGame.multiplier(300,1000)-CrashGame.multiplier(280,1000);
        assertTrue(late>early*4);
        long hundred=CrashGame.limitAfter(100);int alive=0,samples=200_000;
        for(int i=0;i<samples;i++) if(CrashGame.crashAfter((i+.5)/samples,.7968,1000)>hundred)alive++;
        double frequency=alive/(double)samples;
        assertTrue(frequency>.001&&frequency<.002,"Unexpected ×100 tail: "+frequency);
        assertTrue(CrashGame.multiplier(hundred,1000)>=100);
    }
    @Test void enlargedWheelSectorsPreserveAllPrizeWeightsAndPointerResults() {
        assertEquals(20,ColorWheelGame.sectors().size());
        assertEquals(40,ColorWheelGame.sectors().stream().mapToInt(ColorWheelGame.Sector::weight).sum());
        var positions=new HashSet<Integer>();
        for(int pocket=0;pocket<40;pocket++) {
            int position=ColorWheelGame.displayPocket(pocket);assertTrue(positions.add(position));
            var sector=ColorWheelGame.sectors().stream().filter(s->position>=s.start()&&position<s.start()+s.weight()).findFirst().orElseThrow();
            assertEquals(ColorWheelGame.pocket(pocket),sector.color());
        }
        for(var color:ColorWheelGame.COLORS)assertEquals(ColorWheelGame.count(color),ColorWheelGame.sectors().stream().filter(s->s.color().equals(color)).mapToInt(ColorWheelGame.Sector::weight).sum());
    }
    @Test void defaultProgressionMigratesWithoutOverwritingCustomProfiles() {
        var json=new CasinoProgression().toJson();json.addProperty("version",2);
        json.getAsJsonObject("terminal").addProperty("advancement","");
        json.getAsJsonArray("levelCosts").set(1,new com.google.gson.JsonPrimitive(2000));
        var wheel=json.getAsJsonObject("games").getAsJsonObject("roulette");
        wheel.addProperty("maxRunMultiplier",36);wheel.addProperty("payoutScale",.9);
        var defaults=CasinoProgression.fromJson(json);
        assertEquals(1200,defaults.levelCost(2));assertEquals(1,defaults.profile("roulette").payoutScale());
        assertEquals(32,defaults.profile("roulette").maxRunMultiplier());
        wheel.addProperty("maxBet",4321);json.getAsJsonArray("levelCosts").set(1,new com.google.gson.JsonPrimitive(2345));
        var custom=CasinoProgression.fromJson(json);
        assertEquals(2345,custom.levelCost(2));assertEquals(4321,custom.profile("roulette").maxBet());
        assertEquals(.9,custom.profile("roulette").payoutScale());
    }
    @Test void externalModsCannotTradeAndEquipmentCannotBeRecycled() {
        assertFalse(TradePolicy.canTrade("create:iron_sheet"));
        assertFalse(TradePolicy.canTrade("example:diamond"));
        assertFalse(TradePolicy.canTrade("teamecon:scratch_card_match"));
        assertTrue(TradePolicy.canTrade("teamecon:shop_machine"));
        assertFalse(TradePolicy.canSell("teamecon:shop_machine"));
        assertTrue(TradePolicy.canSell("minecraft:iron_ingot"));
        assertFalse(TradePolicy.canSell("minecraft:command_block"));
    }
}
