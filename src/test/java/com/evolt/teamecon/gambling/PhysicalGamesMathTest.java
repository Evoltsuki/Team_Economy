package com.evolt.teamecon.gambling;

import com.evolt.teamecon.scratch.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalGamesMathTest {
    @Test void everyPrintedBoardMatchesItsPrepaidPrizeAcrossAllTiers() {
        for(var kind:ScratchKind.values())for(int bucket=0;bucket<kind.multipliers().length;bucket++)
            for(long seed=0;seed<4000;seed++){
                var board=ScratchBoard.create(kind,bucket,seed);
                assertEquals(kind.multiplier(bucket),board.multiplier(),kind+" bucket "+bucket+" seed "+seed);
                for(int i=0;i<board.size();i++)if(kind==ScratchKind.DICE)assertTrue(board.value(i)>=1&&board.value(i)<=6);
                else if(kind==ScratchKind.VAULT)assertTrue(board.value(i)>=0&&board.value(i)<=9);
            }
    }
    @Test void ticketsUseExactlyTheirPublishedOddsAndConservativeReturns() {
        assertEquals(8,ScratchKind.values().length);
        for(var kind:ScratchKind.values()){
            assertEquals(10000,Arrays.stream(kind.weights()).sum());
            assertTrue(kind.expectedFactor()>0&&kind.expectedFactor()<1);
            int[] observed=new int[kind.weights().length];
            for(int roll=0;roll<10000;roll++){
                final int fixed=roll;
                observed[kind.draw(new Random(){@Override public int nextInt(int bound){assertEquals(10000,bound);return fixed;}})]++;
            }
            assertArrayEquals(kind.weights(),observed);
        }
    }
    @Test void aTicketAlwaysReopensWithTheSamePrintedBoard() {
        for(var kind:ScratchKind.values()){
            var first=ScratchBoard.create(kind,2,-927184721L);var reopened=ScratchBoard.create(kind,2,-927184721L);
            assertEquals(first.target(),reopened.target());
            for(int i=0;i<first.size();i++){assertEquals(first.value(i),reopened.value(i));assertEquals(first.prize(i),reopened.prize(i));}
        }
    }
    @Test void rollingMarblePocketsMatchThePublishedColourDistribution() {
        int[] counts=Arrays.stream(ColorWheelGame.COLORS).mapToInt(ColorWheelGame::count).toArray();
        assertArrayEquals(new int[]{23,9,5,2,1},counts);
        double expected=0;
        for(int i=0;i<ColorWheelGame.POCKETS;i++)expected+=ColorWheelGame.pocket(i).multiplier()/(double)ColorWheelGame.POCKETS;
        assertEquals(.925,expected,1e-10);
        assertEquals(ColorWheelGame.EXPECTED_FACTOR,expected,1e-10);
    }
}
