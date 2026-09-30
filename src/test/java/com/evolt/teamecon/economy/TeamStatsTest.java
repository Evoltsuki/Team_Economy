package com.evolt.teamecon.economy;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TeamStatsTest {
    @Test void everyPurchaseDecreasesNetIncomeAndSurvivesReload() {
        var wallet=UUID.randomUUID();var player=UUID.randomUUID();var stats=new TeamStats();
        TxType[] types={TxType.SELL,TxType.BUY,TxType.SERVICE,TxType.BLINDBOX,TxType.GAMBLE_LOSS,TxType.GAMBLE_WIN,TxType.REFUND};
        long[] amounts={100,-20,-30,-64,-10,18,-5};long sum=0;
        for(int i=0;i<types.length;i++){
            stats.apply(new Transaction(i,wallet,player,"Tester",types[i],"test",1,Math.abs(amounts[i]),amounts[i],0));
            sum+=amounts[i];assertEquals(sum,stats.earningsByPlayer().get(player));
        }
        assertEquals(-11,TeamStats.load(stats.save()).earningsByPlayer().get(player));
    }
    @Test void oldLifetimeSpendingMigratesWithoutLedgerHistory() {
        var id=UUID.randomUUID();var tag=new net.minecraft.nbt.CompoundTag();
        var sales=new net.minecraft.nbt.CompoundTag();sales.putLong(id.toString(),100);
        var spent=new net.minecraft.nbt.CompoundTag();spent.putLong(id.toString(),-150);
        var earnings=new net.minecraft.nbt.CompoundTag();earnings.putLong(id.toString(),90);
        tag.put("salesByPlayer",sales);tag.put("spentByPlayer",spent);tag.put("earningsByPlayer",earnings);
        assertEquals(-50,TeamStats.load(tag).earningsByPlayer().get(id));
        var migrated=TeamStats.load(tag);
        assertEquals(-50,TeamStats.load(migrated.save()).earningsByPlayer().get(id));
    }

    @Test void adminOverridesDoNotCountAsPlayerIncome() {
        var stats=new TeamStats();var id=UUID.randomUUID();var wallet=UUID.randomUUID();
        stats.apply(new Transaction(1,wallet,id,"Tester",TxType.BUY,"stone",1,20,-20,0));
        stats.apply(new Transaction(2,wallet,id,"Tester",TxType.ADMIN,"balance",1,1000,1000,0));
        assertEquals(-20,stats.earningsByPlayer().get(id));
    }
}
