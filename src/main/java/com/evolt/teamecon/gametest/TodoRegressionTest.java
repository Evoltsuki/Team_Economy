package com.evolt.teamecon.gametest;
import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.*;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.shop.ShopMenu;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("teamecon")
public final class TodoRegressionTest {
    @GameTest(template="empty")
    public static void legacyDefaultLimitsMigrateButCustomProfilesStay(GameTestHelper h){
        var json=new CasinoProgression().toJson();json.addProperty("version",1);
        var games=json.getAsJsonObject("games");
        games.getAsJsonObject("penguin").addProperty("maxBet",100);games.getAsJsonObject("penguin").addProperty("maxRunMultiplier",5);
        games.getAsJsonObject("hilo").addProperty("maxBet",13);
        var loaded=CasinoProgression.fromJson(json);
        h.assertTrue(loaded.profile("penguin").maxBet()==10000&&loaded.profile("penguin").maxRunMultiplier()==40,"Old defaults not migrated");
        h.assertTrue(loaded.profile("hilo").maxBet()==13,"Custom cap overwritten");h.succeed();
    }
    @GameTest(template="empty")
    public static void crashClockPersistsAndLateCashoutCannotBeatTheDeadline(GameTestHelper h){
        var p=ProgressionTest.player(h,"crash-clock");var m=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        long now=p.getServer().overworld().getGameTime();m.setBalance(wallet,900);
        var run=new BetSession(100,wallet,"multiplier",1000);run.startCrash(now,25);m.putBet(p.getUUID(),run);
        var loaded=TeamEconomyManager.load(m.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.bet(p.getUUID()).crashDue()==now+25,"Clock rerolled on disk");
        var games=new GamblingService(p.getServer(),loaded);
        h.assertTrue(games.tickCrash(p.getUUID(),"qa",now+20).multiplier()>1,"Server clock did not grow");
        h.assertTrue(games.tickCrash(p.getUUID(),"qa",now+25).status()==GamblingService.Status.BUSTED,"Deadline did not bust");
        h.assertTrue(games.cashOut(p.getUUID(),wallet,"qa","multiplier").payout()==0&&loaded.getBalance(wallet)==900,"Late cashout paid");
        var atDeadline=new BetSession(100,wallet,"multiplier",1000);atDeadline.startCrash(now,0);loaded.putBet(p.getUUID(),atDeadline);
        h.assertTrue(games.cashOut(p.getUUID(),wallet,"qa","multiplier").status()==GamblingService.Status.BUSTED,"Cashout itself failed to check crash time");
        h.assertTrue(loaded.getBalance(wallet)==900,"Crashed funds refunded");h.succeed();
    }
    @GameTest(template="empty")
    public static void successfulHopCollectsImmediatelyAndFailedHopEndsTheRun(GameTestHelper h){
        var p=ProgressionTest.player(h,"layer-hop");var m=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        m.setBalance(wallet,1000);CasinoLevelsTest.unlock(m,wallet,2);var games=new GamblingService(p.getServer(),m);var pos=p.blockPosition();
        var key=SessionKey.machine(p.getUUID(),p.level().dimension().location().toString(),pos);
        var pending=games.beginMachine(p,pos,"penguin","jump",100,false,14);
        h.assertTrue(pending!=null&&m.getBalance(wallet)==900,"Hop did not reserve stake");
        h.assertTrue(games.cashOut(key,wallet,"qa","penguin").status()==GamblingService.Status.BUSY,"Collected during unresolved jump");
        m.putPendingMachine(new PendingMachineGame(pending.player(),pending.wallet(),pending.playerName(),pending.game(),pending.dimension(),pos,
                pending.dueTick(),pending.kind(),100,0,1.2,"jump,1,1",true,pending.tier(),0));
        games.settleMachine(SessionKey.of(pending),pending.dueTick());
        h.assertTrue(games.sessionOf(key).rounds()==1&&games.sessionOf(key).multiplier()==1.2,"Jump skipped a layer");
        h.assertTrue(games.cashOut(key,wallet,"qa","penguin").payout()==120&&m.getBalance(wallet)==1020&&m.bet(key)==null,"Cashout was not immediate");
        h.assertTrue(games.cashOut(key,wallet,"qa","penguin").payout()==0&&m.getBalance(wallet)==1020,"Double payout");
        var loss=games.beginMachine(p,pos,"penguin","jump",100,false,14);
        m.putPendingMachine(new PendingMachineGame(loss.player(),loss.wallet(),loss.playerName(),loss.game(),loss.dimension(),pos,
                loss.dueTick(),loss.kind(),100,0,0,"jump,1,0",false,loss.tier(),0));
        h.assertTrue(games.settleMachine(SessionKey.of(loss),loss.dueTick()).status()==GamblingService.Status.BUSTED&&m.bet(key)==null,"Fall left an active session");
        h.assertTrue(m.getBalance(wallet)==920&&games.cashOut(key,wallet,"qa","penguin").payout()==0,"Fall refunded stake");h.succeed();
    }
    @GameTest(template="empty")
    public static void saleSlotQuotesPaysOnceAndReturnsUnsoldItems(GameTestHelper h){
        var p=ProgressionTest.player(h,"sale-slot");var m=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        var economy=new EconomyService(p.getServer(),m,TeamEconomyMod.get().prices());
        var menu=new ShopMenu(67,p.getInventory());p.getInventory().items.set(9,new ItemStack(Items.DIAMOND,3));
        menu.quickMoveStack(p,ShopMenu.SALE_SLOTS);h.assertTrue(menu.sale().getItem(0).getCount()==3&&p.getInventory().items.get(9).isEmpty(),"Shift-click did not populate sale slot");
        long quote=economy.saleQuote(p,menu.sale().getItem(0));h.assertTrue(quote>0&&m.getBalance(wallet)==0,"Quote mutated wallet");
        var sold=economy.sell(p,menu.sale().getItem(0));h.assertTrue(sold.total()==quote&&menu.sale().isEmpty()&&m.getBalance(wallet)==quote,"Sale differs from immediate quote");
        economy.sell(p,menu.sale().getItem(0));h.assertTrue(m.getBalance(wallet)==quote,"Empty sale duplicated payment");
        menu.sale().setItem(0,new ItemStack(Items.GOLD_INGOT,7));menu.removed(p);
        h.assertTrue(p.getInventory().countItem(Items.GOLD_INGOT)==7&&menu.sale().isEmpty(),"Unsold stack lost on close");h.succeed();
    }
    @GameTest(template="empty")
    public static void directColoursAndWagerFactorWorkInEveryFacing(GameTestHelper h){
        for(Direction front:Direction.Plane.HORIZONTAL){
            var state=ModRegistries.ROULETTE_TABLE.get().defaultBlockState().setValue(CasinoMachineBlock.FACING,front).setValue(CasinoMachineBlock.ASSEMBLED,true);
            var e=new CasinoMachineBlockEntity(BlockPos.ZERO.above(),state);
            var controls=new MachineInteraction.Control[]{MachineInteraction.Control.RED,MachineInteraction.Control.BLACK,MachineInteraction.Control.GREEN};
            for(int i=0;i<3;i++){
                double x=.6+i*.9;Direction right=front.getCounterClockWise(),back=front.getOpposite();
                Vec3 hit=new Vec3(.5+right.getStepX()*(x-.5)-back.getStepX()*.5,.92+MachineLayout.controlsOffset(GameType.ROULETTE),.5+right.getStepZ()*(x-.5)-back.getStepZ()*.5);
                h.assertTrue(MachineInteraction.controlAt(e,new BlockHitResult(hit,front,BlockPos.containing(hit),false))==controls[i],"Wrong direct colour hitbox: "+front);
            }
        }
        h.assertTrue(MachineBetMenu.multiplied(100,100,10000)==10000,"Valid factor rejected");
        h.assertTrue(MachineBetMenu.multiplied(100,101,10000)==0&&MachineBetMenu.multiplied(100,-1,10000)==0,"Over-limit factor accepted");
        h.assertTrue(MachineBetMenu.multiplied(Long.MAX_VALUE,1000,Long.MAX_VALUE)==0,"Factor overflowed");h.succeed();
    }

    @GameTest(template="empty")
    public static void guideIsGrantedOnceEvenWithNoStartingBalance(GameTestHelper h){
        var p=ProgressionTest.player(h,"guide-once");
        com.evolt.teamecon.listener.StartingBalanceHandler.grantGuide(p);
        com.evolt.teamecon.listener.StartingBalanceHandler.grantGuide(p);
        h.assertTrue(p.getInventory().countItem(ModRegistries.GUIDE_BOOK.get())==1,"Duplicate or absent welcome book");
        p.getInventory().clearContent();com.evolt.teamecon.listener.StartingBalanceHandler.grantGuide(p);
        h.assertTrue(p.getInventory().isEmpty(),"Book can be farmed after disposal");h.succeed();
    }
    @GameTest(template="empty")
    public static void scaledCardsPersistStakeAndNeverAcceptUnfundedFactors(GameTestHelper h){
        var p=ProgressionTest.player(h,"scaled-card");var m=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        m.setBalance(wallet,1000000);var service=new com.evolt.teamecon.scratch.ScratchCardService(p.getServer(),m);
        var kind=com.evolt.teamecon.scratch.ScratchKind.MATCH;
        h.assertTrue(!service.buy(p,kind,100).equals("scratch.teamecon.purchased")&&m.getBalance(wallet)==1000000,"Starter can buy a late stake");
        CasinoLevelsTest.unlock(m,wallet,5);m.setBalance(wallet,1000000);
        for(int factor:new int[]{0,-1,10001,Integer.MAX_VALUE})h.assertTrue(!service.buy(p,kind,factor).equals("scratch.teamecon.purchased"),"Invalid factor accepted");
        h.assertTrue(service.buy(p,kind,10000).equals("scratch.teamecon.purchased")&&m.getBalance(wallet)==900000,"Scaled card charged incorrectly");
        var stack=p.getInventory().items.stream().filter(x->x.getItem() instanceof com.evolt.teamecon.scratch.ScratchCardItem).findFirst().orElseThrow();
        var id=com.evolt.teamecon.scratch.ScratchCardItem.serial(stack);var ticket=m.ticket(id);
        var loaded=TeamEconomyManager.load(m.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(ticket.price()==100000&&loaded.ticket(id).equals(ticket)&&ticket.payout()==ticket.price()*kind.multiplier(ticket.bucket()),"Saved scaled prize differs");
        var menu=new com.evolt.teamecon.scratch.ScratchCardMenu(73,p.getInventory(),ticket);p.containerMenu=menu;
        long paid=service.reveal(p,id);h.assertTrue(paid==ticket.payout()&&service.reveal(p,id)==paid&&m.getBalance(wallet)==900000+paid,"Scaled card paid twice");h.succeed();
    }
    @GameTest(template="empty")
    public static void earlyMachinesScaleWithoutChangingTheirOdds(GameTestHelper h){
        var p=ProgressionTest.player(h,"scaling");var m=new TeamEconomyManager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());var games=new GamblingService(p.getServer(),m);
        long before=games.maxBet("hilo",wallet);double odds=games.odds("hilo").maxReturn();
        CasinoLevelsTest.unlock(m,wallet,5);m.setBalance(wallet,1000000);
        h.assertTrue(games.maxBet("hilo",wallet)==Math.min(games.maxBet(),before*25)&&games.odds("hilo").maxReturn()==odds,"Old machines lost usefulness or gained positive expectation");
        h.assertTrue(games.checkBet(wallet,games.maxBet("hilo",wallet),"hilo")==GamblingService.Status.OK&&games.checkBet(wallet,games.maxBet("hilo",wallet)+1,"hilo")==GamblingService.Status.TOO_BIG,"Dynamic cap not enforced");h.succeed();
    }
    @GameTest(template="empty")
    public static void netEarningsSurviveLedgerTrimmingAndSaveReload(GameTestHelper h){
        var m=new TeamEconomyManager();var wallet=java.util.UUID.randomUUID();var id=java.util.UUID.randomUUID();
        for(int i=0;i<240;i++)m.appendTransaction(new Transaction(m.nextTxId(),wallet,id,"seller",TxType.SELL,"stone",1,10,10,0));
        m.appendTransaction(new Transaction(m.nextTxId(),wallet,id,"seller",TxType.GAMBLE_LOSS,"hilo",1,100,-100,0));
        m.appendTransaction(new Transaction(m.nextTxId(),wallet,id,"seller",TxType.GAMBLE_WIN,"hilo",1,180,180,0));
        m.appendTransaction(new Transaction(m.nextTxId(),wallet,id,"seller",TxType.BUY,"stone",1,50,-50,0));
        var loaded=TeamEconomyManager.load(m.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.stats(wallet).earningsByPlayer().get(id)==2430&&loaded.stats(wallet).playerName(id).equals("seller"),"Net earnings omit purchases or depend on trimmed ledger");h.succeed();
    }
    @GameTest(template="empty")
    public static void shopsReserveTwoBlocksAndDropOnlyOneItem(GameTestHelper h){
        var level=h.getLevel();int i=0;
        for(var block:new com.evolt.teamecon.shop.ShopBlock[]{ModRegistries.SHOP_MACHINE.get(),ModRegistries.BLIND_BOX_MACHINE.get()})for(Direction facing:Direction.Plane.HORIZONTAL){
            BlockPos pos=h.absolutePos(new BlockPos(2+i++*4,1,3));var state=block.defaultBlockState().setValue(com.evolt.teamecon.shop.ShopBlock.FACING,facing);
            level.setBlock(pos,state,3);block.setPlacedBy(level,pos,state,null,new ItemStack(block));
            h.assertTrue(level.getBlockEntity(pos) instanceof com.evolt.teamecon.shop.ShopMachineBlockEntity&&level.getBlockEntity(pos.above())==null,"Two owners in cabinet");
            var top=level.getBlockState(pos.above());h.assertTrue(top.is(block)&&top.getValue(com.evolt.teamecon.shop.ShopBlock.FACING)==facing,"Missing or rotated upper half");
            level.destroyBlock(pos.above(),true);
            h.assertTrue(level.isEmptyBlock(pos)&&level.isEmptyBlock(pos.above()),"Orphan cabinet half");
            var items=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(1.5));
            h.assertTrue(items.stream().filter(e->e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"Cabinet drops more than one machine");
        }
        var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);BlockPos pos=h.absolutePos(new BlockPos(2,1,8));level.setBlock(pos.above(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        var block=ModRegistries.SHOP_MACHINE.get();var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(block),new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        h.assertTrue(block.getStateForPlacement(context)==null,"Blocked shop overwrites ceiling");h.succeed();
    }
    @GameTest(template="empty")
    public static void rareCreatureRetailFloorsDoNotIncreaseFarmSellPrices(GameTestHelper h){
        var prices=TeamEconomyMod.get().prices();
        h.assertTrue(prices.shopPrices().itemPrice("minecraft:axolotl_bucket",2,256)>=8192,"Axolotl bucket still costs two");
        h.assertTrue(prices.shopPrices().itemPrice("minecraft:tadpole_bucket",2,256)>=2048,"Creature bucket missed retail floor");h.succeed();
    }
}
