package com.evolt.teamecon.gambling;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CrashGameTest {
    @Test void clockGrowsThenCrashesToZeroAndCannotBeCollectedTwice() {
        var run=new BetSession(1000,UUID.randomUUID(),"multiplier",1000);
        run.startCrash(50,100);
        assertFalse(run.advanceCrash(50));assertEquals(1,run.multiplier());
        double previous=1;
        for(int tick=51;tick<150;tick++){
            assertFalse(run.advanceCrash(tick));assertTrue(run.multiplier()>=previous);previous=run.multiplier();
        }
        assertTrue(previous>1.7);assertTrue(run.advanceCrash(150));
        assertEquals(0,run.multiplier());assertEquals(0,run.cashOutValue());assertTrue(run.isBusted());
        assertEquals(0,run.close());assertEquals(0,run.close());
    }
    @Test void samplingEveryQuantileKeepsEveryFixedCashoutBelowPublishedReturn() {
        int samples=100000;double rtp=.7968;
        for(int tick:new int[]{0,1,20,100,200,500,1000}){
            int alive=0;
            for(int sample=0;sample<samples;sample++)if(CrashGame.crashAfter((sample+.5)/samples,rtp,1000)>tick)alive++;
            double expected=alive/(double)samples*CrashGame.multiplier(tick,1000);
            assertTrue(expected<=rtp+.002,"Cash-out at tick "+tick+" had return "+expected);
        }
    }
    @Test void maximumSurvivorIsStillAliveAtTheLimitAndHasNoUnboundedClock() {
        long limit=CrashGame.limitAfter(1000),due=CrashGame.crashAfter(.99999,.7968,1000);
        assertEquals(limit+1,due);assertEquals(1000,CrashGame.multiplier(limit,1000));
        assertEquals(0,CrashGame.crashAfter(0,.7968,1000));
        assertThrows(IllegalArgumentException.class,()->CrashGame.crashAfter(Double.NaN,.8,1000));
        assertThrows(IllegalArgumentException.class,()->CrashGame.crashAfter(.5,1.1,1000));
    }
    @Test void savingTheHiddenClockDoesNotRerollOrExtendTheRound() {
        UUID wallet=UUID.randomUUID();var first=new BetSession(100,wallet,"multiplier",1000);
        first.startCrash(200,100);first.advanceCrash(240);
        var restored=BetSession.restore(first.stake(),wallet,first.game(),first.maxMultiplier(),first.multiplier(),first.rounds());
        restored.restoreCrash(first.crashStarted(),first.crashDue());
        assertEquals(first.crashDue(),restored.crashDue());assertTrue(restored.advanceCrash(300));assertEquals(0,restored.close());
    }
}
