package com.evolt.teamecon.gambling;

import com.evolt.teamecon.scratch.ScratchKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CasinoProgressionTest {
    @Test void laterMachinesTradeExpectedReturnForLargerPossibleRewards() {
        var rules = new CasinoProgression();
        double early = rules.profile("hilo").payoutScale();
        double hopMin = PenguinGame.jumps().stream().mapToDouble(RiskTier::expectedFactor).min().orElseThrow();
        double hopMax = PenguinGame.jumps().stream().mapToDouble(RiskTier::expectedFactor).max().orElseThrow();
        double colour = ColorWheelGame.EXPECTED_FACTOR;
        double roulette = RouletteGame.EXPECTED_FACTOR * rules.profile("roulette").payoutScale();
        SlotsGame slots = new SlotsGame(); slots.defaults(); double slotReturn = slots.expectedPayout(100);
        assertTrue(hopMax < 1 && hopMin > colour && roulette > slotReturn && roulette < 1);
        assertTrue(early > slotReturn && early < 1);
        assertEquals(2,2*rules.profile("roulette").payoutScale());
        assertEquals(32,rules.profile("roulette").maxRunMultiplier());
        assertEquals(.90, early, 1e-12); assertEquals(.846875, slotReturn, 1e-9);
        assertTrue(.96 * rules.profile("multiplier").chanceScale() < slotReturn);
        long stake = 0;
        for (String game : CasinoProgression.GAMES) {
            var profile = rules.profile(game);
            assertTrue(profile.maxBet() >= stake);
            stake = profile.maxBet();
        }
        assertEquals(40,rules.profile("penguin").maxRunMultiplier());
        assertTrue(rules.profile("multiplier").maxRunMultiplier()>rules.profile("slots").maxRunMultiplier());
        assertEquals(1200,rules.levelCost(2));
    }
    @Test void laterTicketsHaveLowerReturnAndLargerJackpots() {
        var kinds = new ScratchKind[]{ScratchKind.MATCH, ScratchKind.FRUIT, ScratchKind.BINGO, ScratchKind.CROWN};
        double previous = 1; int maximum = 0;
        for (var kind : kinds) {
            assertTrue(kind.expectedFactor() < previous);
            assertEquals(10000, java.util.Arrays.stream(kind.weights()).sum());
            int max = kind.multipliers()[kind.multipliers().length - 1]; assertTrue(max > maximum);
            previous = kind.expectedFactor(); maximum = max;
        }
        assertEquals(.79, ScratchKind.CROWN.expectedFactor(), 1e-12);
    }
}
