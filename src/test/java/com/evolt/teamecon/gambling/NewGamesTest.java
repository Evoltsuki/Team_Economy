package com.evolt.teamecon.gambling;

import com.evolt.teamecon.economy.MoneyMath;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NewGamesTest {
    @Test void rouletteOutsideAndStraightBetsUseTheRequestedIntegerPayouts() {
        List<String> bets = new ArrayList<>(List.of("red","black","odd","even","low","high"));
        for (int i=0;i<37;i++) bets.add("number:"+i);
        for (String bet : bets) {
            double sum=0;
            for (int n=0;n<37;n++) sum+=RouletteGame.multiplier(bet,n);
            assertEquals((bet.startsWith("number:")?32D:36D)/37, sum/37, 1e-12, bet);
        }
        assertEquals(37, Arrays.stream(RouletteGame.WHEEL).distinct().count());
    }
    @Test void zeroLosesEveryOutsideBet() {
        for (String bet : List.of("red","black","odd","even","low","high")) assertEquals(0,RouletteGame.multiplier(bet,0));
        assertEquals(32,RouletteGame.multiplier("number:0",0));
        for (String bet : List.of("number:-1","number:37","number:no","colour","")) assertFalse(RouletteGame.validChoice(bet));
    }
    @Test void smallStakesNeverExceedTheoreticalReturn() {
        for(long stake : new long[]{1,7,101,10000}) {
            long total=0;
            for(int n=0;n<37;n++) total+=MoneyMath.payout(stake,RouletteGame.multiplier("red",n));
            assertTrue(total<=stake*37);
        }
    }
    @Test void everyLayerIncreasesTheTotalPayoutWhileKeepingTheHouseEdge() {
        double current=1,previousChance=1;
        for(int layer=0;layer<10;layer++) {
            RiskTier jump=PenguinGame.next(layer,current,40,1);
            assertTrue(jump.isValid());assertTrue(jump.expectedFactor()<=.96+1e-12);
            assertTrue(jump.successChance()<previousChance);previousChance=jump.successChance();
            double next=jump.applySuccess(current);assertTrue(next>current);
            assertEquals(PenguinGame.layerMultiplier(layer+1),next,1e-12);current=next;
        }
        assertEquals(40,current,1e-12);
        assertNull(PenguinGame.jump("teleport"));
    }
    @Test void sessionsPayExactlyOnceAndIgnoreClosedRounds() {
        BetSession run=new BetSession(100);
        run.applySuccess(new RiskTier("test",.5,1));
        assertEquals(200,run.close()); assertEquals(0,run.close());
        run.applySuccess(new RiskTier("test",.5,1));
        assertEquals(2,run.multiplier()); assertEquals(1,run.rounds());
    }
    @Test void savedRunsRetainTheirOriginalWalletAndCap() {
        UUID wallet=UUID.randomUUID();
        BetSession run=BetSession.restore(100,wallet,"penguin",10,9,5);
        run.applySuccess(PenguinGame.next(run.rounds(),run.multiplier(),run.maxMultiplier(),1));
        assertEquals(10,run.multiplier()); assertEquals(wallet,run.wallet()); assertEquals("penguin",run.game());
        assertTrue(run.atLimit()); assertEquals(1000,run.close());
        assertThrows(IllegalArgumentException.class,()->BetSession.restore(100,wallet,"penguin",10,Double.NaN,5));
        assertFalse(new RiskTier("test",.5,Double.POSITIVE_INFINITY).isValid());
    }
}
