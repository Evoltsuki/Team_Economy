package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.*;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.gambling.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.scratch.*;
import com.evolt.teamecon.team.TeamUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.nio.file.Files;
import java.util.UUID;

@GameTestHolder("teamecon")
public final class PhysicalGamesTest {
    private PhysicalGamesTest(){}
    private static ServerPlayer player(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"physical-qa"));
        p.getInventory().clearContent();p.containerMenu=p.inventoryMenu;
        var pos=h.absolutePos(new BlockPos(6,1,9));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return p;
    }
    private static UUID wallet(GameTestHelper h,ServerPlayer p){return TeamUtil.walletKey(h.getLevel().getServer(),p.getUUID());}
    private static TeamEconomyManager roundTrip(GameTestHelper h,TeamEconomyManager manager) throws Exception {
        var path=Files.createTempFile("teamecon-physical-",".dat");
        try{NbtIo.writeCompressed(manager.save(new CompoundTag(),h.getLevel().registryAccess()),path);
            return TeamEconomyManager.load(NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()),h.getLevel().registryAccess());
        }finally{Files.deleteIfExists(path);}
    }
    @GameTest(template="empty")
    public static void allEightTicketsArePaidPhysicalItemsAndSettleOnce(GameTestHelper h){
        var p=player(h);var manager=new TeamEconomyManager();var service=new ScratchCardService(h.getLevel().getServer(),manager);var wallet=wallet(h,p);
        manager.setBalance(wallet,100000);CasinoLevelsTest.unlock(manager,wallet,5);
        for(var kind:ScratchKind.values()){
            long before=manager.getBalance(wallet);
            h.assertTrue(service.buy(p,kind).equals("scratch.teamecon.purchased"),"Could not buy "+kind);
            ItemStack card=p.getInventory().items.stream().filter(s->s.is(ModRegistries.SCRATCH_CARDS.get(kind).get())).findFirst().orElseThrow();
            UUID serial=ScratchCardItem.serial(card);var ticket=manager.ticket(serial);
            h.assertTrue(ticket!=null&&ticket.kind()==kind&&manager.getBalance(wallet)==before-kind.price(),"Card was not charged and serialized");
            h.assertTrue(card.getMaxStackSize()==1,"Tickets must not stack distinct serials");
            var menu=new ScratchCardMenu(1,p.getInventory(),ticket);p.containerMenu=menu;
            long actual=service.reveal(p,serial);
            h.assertTrue(actual==ticket.payout()&&card.isEmpty()&&manager.ticket(serial)==null,"Reveal did not consume and pay the ticket");
            h.assertTrue(service.reveal(p,serial)==actual,"A lost acknowledgement must be retryable");
            h.assertTrue(manager.getBalance(wallet)==before-kind.price()+ticket.payout(),"Repeated reveal credited twice");
            p.containerMenu=p.inventoryMenu;
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void ticketTransferAndCopiedSerialCannotDuplicateRewards(GameTestHelper h) throws Exception {
        var buyer=player(h);var holder=player(h);var manager=new TeamEconomyManager();
        var kind=ScratchKind.CROWN;var serial=UUID.randomUUID();
        var ticket=new ScratchTicket(serial,kind,kind.price(),5,kind.price()*500,7234);
        manager.putTicket(ticket);manager.setBalance(wallet(h,buyer),20);manager.setBalance(wallet(h,holder),30);
        manager=roundTrip(h,manager);h.assertTrue(manager.ticket(serial).equals(ticket),"Paid card changed on disk reload");
        var service=new ScratchCardService(h.getLevel().getServer(),manager);
        ItemStack card=new ItemStack(ModRegistries.SCRATCH_CARDS.get(kind).get());ScratchCardItem.stamp(card,serial);
        buyer.containerMenu=new ScratchCardMenu(3,buyer.getInventory(),ticket);
        h.assertTrue(service.reveal(buyer,serial)==-1,"A ticket menu without the physical item paid out");
        holder.getInventory().items.set(0,card.copy());holder.containerMenu=new ScratchCardMenu(4,holder.getInventory(),ticket);
        h.assertTrue(service.reveal(holder,serial)==ticket.payout(),"New holder could not scratch transferred ticket");
        h.assertTrue(manager.getBalance(wallet(h,holder))==30+ticket.payout()&&manager.getBalance(wallet(h,buyer))==20,"Payout went to the wrong holder");
        buyer.getInventory().items.set(0,card.copy());
        h.assertTrue(service.reveal(buyer,serial)==-1&&manager.getBalance(wallet(h,buyer))==20,"Copied serial paid twice");
        var reloaded=roundTrip(h,manager);
        h.assertTrue(reloaded.ticket(serial)==null&&reloaded.getBalance(wallet(h,holder))==30+ticket.payout(),"Used serial revived after reload");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void ticketPurchaseRejectsFullInventoryAndBlankShopItems(GameTestHelper h){
        var p=player(h);var manager=new TeamEconomyManager();var service=new ScratchCardService(h.getLevel().getServer(),manager);
        manager.setBalance(wallet(h,p),10000);
        for(int i=0;i<36;i++)p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        h.assertTrue(service.buy(p,ScratchKind.MATCH).equals("command.teamecon.shop_no_space")&&manager.getBalance(wallet(h,p))==10000,"Failed card delivery charged funds");
        for(var kind:ScratchKind.values()){
            String id="teamecon:scratch_card_"+kind.id();
            h.assertTrue(!TeamEconomyMod.get().prices().resolve(id).known(),"Blank card got a generic price");
            h.assertTrue(!TeamEconomyMod.get().shop().itemCatalog().contains(id),"Blank card leaked into normal item shop");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void wheelPaymentWaitsForAnimationAndSurvivesDiskReload(GameTestHelper h) throws Exception {
        var p=player(h);var manager=new TeamEconomyManager();UUID wallet=wallet(h,p);manager.setBalance(wallet,10000);CasinoLevelsTest.unlock(manager,wallet,5);
        var games=new GamblingService(h.getLevel().getServer(),manager);var pos=h.absolutePos(new BlockPos(6,2,6));
        var pending=games.beginMachine(p,pos,"color_wheel","",100,false,140);
        h.assertTrue(pending!=null&&manager.getBalance(wallet)==9900,"Start must reserve exactly one stake");
        h.assertTrue(games.beginMachine(p,pos,"color_wheel","",100,false,140)==null,"Busy machine accepted duplicate start");
        h.assertTrue(games.settleMachine(SessionKey.of(pending),pending.dueTick()-1).status()==GamblingService.Status.BUSY&&manager.getBalance(wallet)==9900,"Prize paid before animation finished");
        var loaded=roundTrip(h,manager);h.assertTrue(loaded.pendingMachine(p.getUUID()).equals(pending),"Pending wheel changed on disk");
        games=new GamblingService(h.getLevel().getServer(),loaded);var result=games.settleMachine(SessionKey.of(pending),pending.dueTick());
        h.assertTrue(result.payout()==pending.payout()&&loaded.getBalance(wallet)==9900+pending.payout(),"Final wheel payment mismatch");
        games.settleMachine(SessionKey.of(pending),pending.dueTick()+200);
        h.assertTrue(loaded.getBalance(wallet)==9900+pending.payout()&&loaded.pendingMachine(p.getUUID())==null,"Repeated settlement paid twice");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void physicalRunIsBoundToOneMachineAndCashOutIsDelayed(GameTestHelper h) throws Exception {
        var p=player(h);var manager=new TeamEconomyManager();UUID wallet=wallet(h,p);manager.setBalance(wallet,10000);CasinoLevelsTest.unlock(manager,wallet,5);
        var games=new GamblingService(h.getLevel().getServer(),manager);var pos=h.absolutePos(new BlockPos(6,2,6));
        var key=SessionKey.machine(p.getUUID(),p.level().dimension().location().toString(),pos);
        var pending=games.beginMachine(p,pos,"penguin","jump",100,false,70);
        h.assertTrue(pending!=null&&games.sessionOf(key).rounds()==0,"Run advanced before the world jump finished");
        games.settleMachine(SessionKey.of(pending),pending.dueTick());
        if(pending.won())h.assertTrue(games.sessionOf(key).rounds()==1,"Completed jump did not advance run");
        else h.assertTrue(games.sessionOf(key)==null,"Failed jump left usable escrow");
        // A known won run lets this regression cover cash-out on every execution.
        var run=BetSession.restore(100,wallet,"penguin",1000,2.25,3);run.bindMachine(p.level().dimension().location().toString(),pos);manager.putBet(p.getUUID(),run);
        manager=roundTrip(h,manager);games=new GamblingService(h.getLevel().getServer(),manager);
        h.assertTrue(games.sessionOf(key).atMachine(p.level().dimension().location().toString(),pos),"Machine binding did not persist");
        h.assertTrue(games.cashOut(SessionKey.machine(p.getUUID(),key.dimension(),pos.east(5)),wallet,"qa","penguin").status()==GamblingService.Status.NO_SESSION,"Wrong cabinet collected this run");
        h.assertTrue(games.playRound(p.getUUID(),wallet,"penguin","jump").status()==GamblingService.Status.NO_SESSION,"GUI bypassed world animation");
        long before=manager.getBalance(wallet);var cash=games.beginMachine(p,pos,"penguin","",0,true,30);
        h.assertTrue(cash!=null&&manager.getBalance(wallet)==before&&games.sessionOf(key)==null,"Cash-out did not reserve pending payment");
        games.settleMachine(SessionKey.of(cash),cash.dueTick()-1);h.assertTrue(manager.getBalance(wallet)==before,"Cash-out credited early");
        games.settleMachine(SessionKey.of(cash),cash.dueTick());h.assertTrue(manager.getBalance(wallet)==before+225,"Cash-out value was lost");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void breakingMachineDuringSpinStillSettlesOfflineOwner(GameTestHelper h){
        var p=player(h);var mod=TeamEconomyMod.get();var manager=mod.economy().manager();var wallet=wallet(h,p);manager.setBalance(wallet,10000);CasinoLevelsTest.unlock(manager,wallet,5);
        BlockPos base=h.absolutePos(new BlockPos(6,1,6));var block=ModRegistries.COLOR_WHEEL_TABLE.get();
        var state=block.defaultBlockState().setValue(CasinoMachineBlock.ASSEMBLED,true).setValue(CasinoMachineBlock.FACING,Direction.SOUTH);
        h.getLevel().setBlock(base,state,2);block.setPlacedBy(h.getLevel(),base,state,p,new ItemStack(block));
        var entity=(CasinoMachineBlockEntity)h.getLevel().getBlockEntity(base.above());
        var hit=new BlockHitResult(new Vec3(base.getX()+1.2,base.getY()+.8,base.getZ()+1),Direction.SOUTH,base.above().east(),false);
        MachineInteraction.use(h.getLevel(),base.above(),p,hit);
        var pending=manager.pendingMachine(p.getUUID());
        h.assertTrue(pending!=null&&p.containerMenu==p.inventoryMenu&&entity.spinTicks()>0,"Physical start opened a screen or skipped animation");
        h.getLevel().destroyBlock(base.above().east(),true);
        h.assertTrue(manager.getBalance(wallet)==9900,"Breaking the cabinet paid early");
        h.runAfterDelay(145,()->{
            h.assertTrue(manager.pendingMachine(p.getUUID())==null&&manager.getBalance(wallet)==9900+pending.payout(),"Offline/broken-machine payment was lost");
            h.succeed();
        });
    }
}
