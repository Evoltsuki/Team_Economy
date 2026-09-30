package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("teamecon")
public final class ConcurrentMachinesTest {
    @GameTest(template="empty")
    public static void onePlayerStartsThreeCabinetsAndEachPaysExactlyOnce(GameTestHelper h) {
        var p=ProgressionTest.player(h,"parallel-instant");
        var m=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        m.setBalance(wallet,1000);m.setCasinoLevel(wallet,5);
        var games=new GamblingService(p.getServer(),m);
        var a=games.beginMachine(p,p.blockPosition(),"color_wheel","",100,false,70);
        var b=games.beginMachine(p,p.blockPosition().east(4),"hilo","high",100,false,24);
        var c=games.beginMachine(p,p.blockPosition().west(4),"roulette","red",100,false,70);
        h.assertTrue(a!=null&&b!=null&&c!=null&&m.pendingMachines().size()==3&&m.getBalance(wallet)==700,"Parallel stakes rejected or overwritten");
        h.assertTrue(games.beginMachine(p,a.pos(),"color_wheel","",100,false,70)==null&&m.getBalance(wallet)==700,"Duplicate click charged twice");
        h.assertTrue(games.settleMachine(SessionKey.of(a),a.dueTick()-1).status()==GamblingService.Status.BUSY,"Early payment");
        m=TeamEconomyManager.load(m.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        games=new GamblingService(p.getServer(),m);
        h.assertTrue(m.pendingMachines().size()==3,"Reload lost a pending payment");
        games.settleMachine(SessionKey.of(b),b.dueTick());
        h.assertTrue(m.pendingMachines().size()==2&&m.getBalance(wallet)==700+b.payout(),"Settling B affected A/C");
        games.settleMachine(SessionKey.of(a),a.dueTick());games.settleMachine(SessionKey.of(c),c.dueTick());
        games.settleMachine(SessionKey.of(a),a.dueTick()+1);
        h.assertTrue(m.pendingMachines().isEmpty()&&m.getBalance(wallet)==700+a.payout()+b.payout()+c.payout(),"Lost or duplicate payment");h.succeed();
    }

    @GameTest(template="empty")
    public static void sharedWalletCannotOverspendAndCabinetHasOneOperator(GameTestHelper h) {
        var p=ProgressionTest.player(h,"parallel-owner");var other=ProgressionTest.player(h,"parallel-other");
        var m=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        var otherWallet=TeamUtil.walletKey(p.getServer(),other.getUUID());
        m.setBalance(wallet,150);m.setCasinoLevel(wallet,5);m.setBalance(otherWallet,1000);m.setCasinoLevel(otherWallet,5);
        var games=new GamblingService(p.getServer(),m);var pos=p.blockPosition();
        h.assertTrue(games.beginMachine(p,pos,"color_wheel","",100,false,70)!=null,"First stake refused");
        h.assertTrue(games.beginMachine(other,pos,"color_wheel","",100,false,70)==null&&m.getBalance(otherWallet)==1000,"Other operator stole cabinet");
        h.assertTrue(games.beginMachine(p,pos.east(4),"hilo","high",100,false,24)==null&&m.getBalance(wallet)==50,"Parallel machines overdrew wallet");
        h.assertTrue(games.playInstant(p.getUUID(),wallet,"qa",100,"hilo","high").status()==GamblingService.Status.NO_FUNDS,"Terminal ignored reserved stakes");h.succeed();
    }

    @GameTest(template="empty")
    public static void terminalAndPhysicalRunsAreIndependentAndCashoutReturnsOriginalWallet(GameTestHelper h) {
        var p=ProgressionTest.player(h,"parallel-runs");var m=new TeamEconomyManager();
        var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());m.setBalance(wallet,1000);m.setCasinoLevel(wallet,5);
        var games=new GamblingService(p.getServer(),m);var a=p.blockPosition();var dim=p.level().dimension().location().toString();
        h.assertTrue(games.startBet(p.getUUID(),wallet,"qa",100,"penguin").accepted(),"Terminal start failed");
        var first=games.beginMachine(p,a,"penguin","jump",100,false,14);
        var second=games.beginMachine(p,a.east(4),"penguin","jump",100,false,14);
        h.assertTrue(first!=null&&second!=null&&m.activeBets().size()==3&&m.getBalance(wallet)==700,"Runs share one player slot");
        var firstKey=SessionKey.of(first);var secondKey=SessionKey.of(second);
        h.assertTrue(games.cashOut(firstKey,wallet,"qa","penguin").status()==GamblingService.Status.BUSY,"Unresolved hop paid early");
        h.assertTrue(games.cashOut(SessionKey.machine(p.getUUID(),"minecraft:the_nether",a),wallet,"qa","penguin").status()==GamblingService.Status.NO_SESSION,"Dimension leaked");
        var stranger=SessionKey.machine(java.util.UUID.randomUUID(),dim,a);
        h.assertTrue(games.cashOut(stranger,wallet,"qa","penguin").status()==GamblingService.Status.NO_SESSION,"Another player collected a run");
        h.assertTrue(games.cashOut(p.getUUID(),java.util.UUID.randomUUID(),"qa","penguin").payout()==100,"Terminal could not cash out while machines busy");
        h.assertTrue(m.getBalance(wallet)==800&&m.bet(firstKey)!=null&&m.bet(secondKey)!=null,"Terminal affected cabinet escrow");
        games.settleMachine(firstKey,first.dueTick());games.settleMachine(secondKey,second.dueTick());
        long expected=800;
        if(first.won())expected+=m.bet(firstKey).cashOutValue();
        if(second.won())expected+=m.bet(secondKey).cashOutValue();
        games.cashOut(firstKey,java.util.UUID.randomUUID(),"qa","penguin");games.cashOut(secondKey,wallet,"qa","penguin");
        h.assertTrue(m.getBalance(wallet)==expected&&m.activeBets().isEmpty(),"Cabinet funds returned to wrong wallet or run");h.succeed();
    }

    @GameTest(template="empty")
    public static void multipleCrashClocksPersistAndBustSeparately(GameTestHelper h) {
        var p=ProgressionTest.player(h,"parallel-crash");var m=new TeamEconomyManager();var wallet=p.getUUID();
        long now=p.getServer().overworld().getGameTime();String dim=p.level().dimension().location().toString();
        var a=new BetSession(100,wallet,"multiplier",1000);a.startCrash(now,20);a.bindMachine(dim,new BlockPos(1,2,3));
        var b=new BetSession(200,wallet,"multiplier",1000);b.startCrash(now,40);b.bindMachine(dim,new BlockPos(5,2,3));
        var t=new BetSession(300,wallet,"multiplier",1000);t.startCrash(now,60);
        m.putBet(p.getUUID(),a);m.putBet(p.getUUID(),b);m.putBet(p.getUUID(),t);
        m=TeamEconomyManager.load(m.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        var games=new GamblingService(p.getServer(),m);var ka=SessionKey.of(p.getUUID(),a);var kb=SessionKey.of(p.getUUID(),b);
        h.assertTrue(m.activeBets().size()==3&&m.bet(kb).crashDue()==now+40,"Reload lost/restarted clock");
        h.assertTrue(games.tickCrash(ka,"qa",now+20).status()==GamblingService.Status.BUSTED,"A missed deadline");
        h.assertTrue(games.tickCrash(kb,"qa",now+20).status()==GamblingService.Status.OK&&m.bet(kb)!=null&&m.bet(p.getUUID())!=null,"A ended B or terminal");
        games.tickCrash(kb,"qa",now+40);games.tickCrash(p.getUUID(),"qa",now+60);
        h.assertTrue(m.activeBets().isEmpty()&&m.getBalance(wallet)==0,"Crash paid or remained active");h.succeed();
    }

    @GameTest(template="empty")
    public static void legacyPlayerKeyedSaveMigratesWithoutLosingEscrow(GameTestHelper h) {
        var p=ProgressionTest.player(h,"parallel-legacy");var wallet=p.getUUID();var pos=new BlockPos(2,3,4);
        var run=new CompoundTag();run.putLong("stake",100);run.putUUID("wallet",wallet);run.putString("game","penguin");
        run.putDouble("maxMultiplier",40);run.putDouble("multiplier",1.2);run.putInt("rounds",1);
        run.putLong("machinePos",pos.asLong());run.putString("machineDimension","minecraft:overworld");
        var bets=new CompoundTag();bets.put(p.getUUID().toString(),run);var old=new CompoundTag();old.put("activeBets",bets);
        var m=TeamEconomyManager.load(old,h.getLevel().registryAccess());var key=SessionKey.machine(p.getUUID(),"minecraft:overworld",pos);
        h.assertTrue(m.bet(key)!=null&&m.bet(p.getUUID())==null,"Old world-bound run became terminal session");
        var games=new GamblingService(p.getServer(),m);
        h.assertTrue(games.cashOut(key,wallet,"qa","penguin").payout()==120&&m.getBalance(wallet)==120,"Migration lost escrow");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void serverTickPaysMultipleOfflineCabinets(GameTestHelper h) {
        var p=ProgressionTest.player(h,"parallel-offline");var mod=TeamEconomyMod.get();var m=mod.economy().manager();
        var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());m.setBalance(wallet,1000);m.setCasinoLevel(wallet,5);
        var a=mod.gambling().beginMachine(p,h.absolutePos(new BlockPos(3,2,3)),"color_wheel","",100,false,14);
        var b=mod.gambling().beginMachine(p,h.absolutePos(new BlockPos(8,2,3)),"hilo","high",100,false,24);
        h.assertTrue(a!=null&&b!=null,"Offline test could not start both machines");
        h.runAfterDelay(35,()->{
            h.assertTrue(m.pendingMachine(SessionKey.of(a))==null&&m.pendingMachine(SessionKey.of(b))==null,"Tick left pending cabinets");
            h.assertTrue(m.getBalance(wallet)==800+a.payout()+b.payout(),"Offline server tick lost a payment");h.succeed();
        });
    }
}
