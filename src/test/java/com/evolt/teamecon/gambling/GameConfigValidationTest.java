package com.evolt.teamecon.gambling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class GameConfigValidationTest {
    @TempDir Path dir;
    @Test void excessiveScratchReturnActuallyDisablesTheGame() throws Exception {
        Files.writeString(dir.resolve("teamecon_scratch_pool.json"),"""
                [{"chance":1,"payoutMultiplier":2,"label":"jackpot"}]
                """);
        ScratchPools game=new ScratchPools(); game.load(dir,1);
        assertFalse(game.ready());
    }
    @Test void scratchWeightsAreNormalisedForDisplayAndDrawing() throws Exception {
        Files.writeString(dir.resolve("teamecon_scratch_pool.json"),"""
                [{"chance":75,"payoutMultiplier":0,"label":"nothing"},{"chance":25,"payoutMultiplier":2,"label":"double"}]
                """);
        ScratchPools game=new ScratchPools(); game.load(dir,1);
        assertTrue(game.ready()); assertEquals(1,game.all().stream().mapToDouble(ScratchPools.Entry::chance).sum(),1e-12);
        assertEquals(.5,game.all().stream().mapToDouble(e->e.chance()*e.payoutMultiplier()).sum(),1e-12);
    }
    @Test void slotPairThatAlsoPaysOnTriplesIsIncludedInValidation() throws Exception {
        Files.writeString(dir.resolve("teamecon_slots.json"),"""
                {"reel":[{"symbol":"cherry","weight":1}],"payouts":[
                  {"symbol":"cherry","matches":2,"multiplier":2},
                  {"symbol":"cherry","matches":3,"multiplier":1}]}
                """);
        SlotsGame game=new SlotsGame(); game.load(dir,1);
        assertFalse(game.ready());
    }
    @Test void zeroSlotWeightsCannotLeaveAReadyButUnplayableGame() throws Exception {
        Files.writeString(dir.resolve("teamecon_slots.json"),"""
                {"reel":[{"symbol":"cherry","weight":0}],"payouts":[{"symbol":"cherry","matches":3,"multiplier":1}]}
                """);
        SlotsGame game=new SlotsGame(); game.load(dir,1); assertFalse(game.ready());
    }
    @Test void malformedConfigsFailClosed() throws Exception {
        for(String file:new String[]{"teamecon_slots.json","teamecon_scratch_pool.json","teamecon_risk_tiers.json"})
            Files.writeString(dir.resolve(file),"{broken");
        SlotsGame slots=new SlotsGame(); slots.load(dir,1); assertFalse(slots.ready());
        ScratchPools scratch=new ScratchPools(); scratch.load(dir,1); assertFalse(scratch.ready());
        RiskTiers tiers=new RiskTiers(); tiers.load(dir,1); assertTrue(tiers.all().isEmpty());
    }
    @Test void lowerCapsAlsoApplyToGeneratedDefaults() {
        SlotsGame slots=new SlotsGame(); slots.load(dir,.1); assertFalse(slots.ready());
        ScratchPools scratch=new ScratchPools(); scratch.load(dir,.1); assertFalse(scratch.ready());
        RiskTiers tiers=new RiskTiers(); tiers.load(dir,.1); assertTrue(tiers.all().isEmpty());
    }
}
