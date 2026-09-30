package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.casino.*;
import com.evolt.teamecon.economy.*;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.price.*;
import com.evolt.teamecon.shop.*;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("teamecon")
public final class RevisionRegressionTest {
    @GameTest(template="empty")
    public static void loweredRouletteControlsAndClearance(GameTestHelper h) {
        for(Direction front:Direction.Plane.HORIZONTAL){
            var state=ModRegistries.ROULETTE_TABLE.get().defaultBlockState()
                    .setValue(CasinoMachineBlock.FACING,front).setValue(CasinoMachineBlock.ASSEMBLED,true);
            var e=new CasinoMachineBlockEntity(BlockPos.ZERO.above(),state);
            Direction right=front.getCounterClockWise(),back=front.getOpposite();
            double x=.6;
            for(double y:new double[]{.72,.97}){
                Vec3 hit=new Vec3(.5+right.getStepX()*(x-.5)-back.getStepX()*.5,y,.5+right.getStepZ()*(x-.5)-back.getStepZ()*.5);
                h.assertTrue(MachineInteraction.controlAt(e,new BlockHitResult(hit,front,BlockPos.containing(hit),false))
                        ==(y<.8?MachineInteraction.Control.START:MachineInteraction.Control.NONE),"Start hitbox did not follow lowered panel: "+front);
            }
            Vec3 wheel=new Vec3(.5+right.getStepX()+back.getStepX(),.9375,.5+right.getStepZ()+back.getStepZ());
            h.assertTrue(MachineInteraction.controlAt(e,new BlockHitResult(wheel,Direction.UP,BlockPos.containing(wheel),false))
                    ==MachineInteraction.Control.WHEEL,"Lowered wheel cannot be selected: "+front);
        }
        h.assertTrue(MachineLayout.rouletteShape(0,1,0).isEmpty(),"Invisible front collision obscures wheel");
        h.assertTrue(!MachineLayout.rouletteShape(1,1,1).isEmpty()&&!MachineLayout.rouletteShape(0,1,2).isEmpty(),"Spindle or sign lost collision");
        h.assertTrue(MachineLayout.rouletteShape(0,0,0).max(Direction.Axis.Y)<.85,"Front panel remains too high");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void adminTestingCommandsUnlockAndRespectPermissions(GameTestHelper h) throws Exception {
        var p=ProgressionTest.player(h,"admin-test");
        var dispatcher=p.getServer().getCommands().getDispatcher();
        var source=p.createCommandSourceStack().withPermission(2);
        var manager=TeamEconomyMod.get().economy().manager();
        var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        manager.setBalance(wallet,1234);
        h.assertTrue(dispatcher.execute("teamecon admin unlock",source)==1&&manager.casinoLevel(wallet)==5,"Unlock did not set Lv.5");
        var end=p.getServer().getAdvancements().get(net.minecraft.resources.ResourceLocation.parse("minecraft:end/kill_dragon"));
        h.assertTrue(!p.getAdvancements().getOrStartProgress(end).isDone()
                &&manager.bypassesProgression(p.getUUID()),"Unlock must bypass mod gates without awarding advancements");
        dispatcher.execute("teamecon admin level 2",source);
        dispatcher.execute("teamecon admin advancements",source);
        h.assertTrue(manager.casinoLevel(wallet)==2&&manager.getBalance(wallet)==1234,"Testing commands spent money or changed unrelated level");
        h.assertTrue(dispatcher.execute("teamecon admin status",source)==1,"Status command failed");
        dispatcher.execute("teamecon admin kit",source);
        h.assertTrue(p.getInventory().countItem(ModRegistries.BLIND_BOX_MACHINE_ITEM.get())==1,"Kit omitted blind box machine");
        boolean denied=false;
        try {dispatcher.execute("teamecon admin unlock",source.withPermission(0));}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}
        h.assertTrue(denied&&manager.casinoLevel(wallet)==2,"Non-OP changed progression");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void expandedPrizesExistAndPurchasesReduceTheLiveBoard(GameTestHelper h) throws Exception {
        var mod=TeamEconomyMod.get();
        Path dir=Files.createTempDirectory("teamecon-reward-test-");
        try {
            var pools=new BlindBoxPools();pools.load(dir,mod.prices());
            h.assertTrue(pools.all().size()==2,"Expanded pools failed validation with real item prices");
            for(String egg:BoxPrizePolicy.EGGS) h.assertTrue(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(egg)) instanceof SpawnEggItem,"Missing spawn egg: "+egg);
        } finally {try(var files=Files.list(dir)){for(Path file:files.toList())Files.delete(file);}Files.delete(dir);}
        var p=ProgressionTest.player(h,"board-buy");var manager=mod.economy().manager();
        var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());manager.setBalance(wallet,1000);
        manager.appendTransaction(new Transaction(manager.nextTxId(),wallet,p.getUUID(),"board-buy",TxType.SELL,"minecraft:stone",1,100,100,0));
        var result=mod.economy().buy(p,Items.STONE,1);
        h.assertTrue(result.outcome()==EconomyService.Outcome.OK,"Board test purchase failed");
        var rows=com.google.gson.JsonParser.parseString(com.evolt.teamecon.network.TeamBoardNetwork.snapshot(p)).getAsJsonObject().getAsJsonArray("members");
        h.assertTrue(rows.get(0).getAsJsonObject().get("earned").getAsLong()==100-result.total(),"Live board omitted purchase debit");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void batchSalesQuoteSharedDemandPayOnceAndReturnUnsupportedItems(GameTestHelper h) {
        var p=ProgressionTest.player(h,"batch-sale");var manager=new TeamEconomyManager();
        var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());var prices=TeamEconomyMod.get().prices();
        var economy=new EconomyService(p.getServer(),manager,prices);
        var menu=new ShopMenu(81,p.getInventory());
        p.getInventory().items.set(9,new ItemStack(Items.IRON_INGOT,32));
        p.getInventory().items.set(10,new ItemStack(Items.IRON_BLOCK,8));
        p.getInventory().items.set(11,new ItemStack(Items.DIAMOND,4));
        for(int i=0;i<3;i++)menu.quickMoveStack(p,ShopMenu.SALE_SLOTS+i);
        h.assertTrue(menu.sale().getItem(2).is(Items.DIAMOND)&&menu.sale().getContainerSize()==27,"Batch deposit routing lost a stack");
        menu.sale().setItem(26,new ItemStack(ModRegistries.GUIDE_BOOK.get()));
        long quote=economy.saleQuote(p,menu.sale());
        h.assertTrue(quote>0&&manager.getBalance(wallet)==0&&manager.ledger(wallet).isEmpty(),"Quote mutated funds");
        var singleManager=new TeamEconomyManager();var singles=new EconomyService(p.getServer(),singleManager,prices);
        long separate=0;for(int i=0;i<3;i++)separate+=singles.sell(p,menu.sale().getItem(i).copy()).total();
        h.assertTrue(Math.abs(quote-separate)<=1,"Shared material demand was counted independently per sale slot");
        var sold=economy.sellBatch(p,menu.sale());
        h.assertTrue(sold.soldCount()==44&&Math.abs(sold.total()-quote)<=1&&manager.getBalance(wallet)==sold.total(),"Batch quote or settlement differs");
        h.assertTrue(menu.sale().getItem(26).is(ModRegistries.GUIDE_BOOK.get()),"Unsupported item was consumed");
        economy.sellBatch(p,menu.sale());h.assertTrue(manager.getBalance(wallet)==sold.total(),"Batch was paid twice");
        menu.removed(p);h.assertTrue(menu.sale().isEmpty()&&p.getInventory().countItem(ModRegistries.GUIDE_BOOK.get())==1,"Unsupported item lost when closing");h.succeed();
    }

    @GameTest(template="empty")
    public static void batchSaleWithAFullWalletDoesNotPartiallyConsumeDeposits(GameTestHelper h) {
        var p=ProgressionTest.player(h,"full-batch");var manager=new TeamEconomyManager();
        var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());manager.setBalance(wallet,MoneyMath.MAX_MONEY-1);
        var economy=new EconomyService(p.getServer(),manager,TeamEconomyMod.get().prices());
        var deposits=new SimpleContainer(new ItemStack(Items.DIAMOND,8),new ItemStack(Items.GOLD_INGOT,8));
        var result=economy.sellBatch(p,deposits);
        h.assertTrue(result.outcome()==EconomyService.Outcome.BALANCE_FULL&&deposits.getItem(0).getCount()==8&&deposits.getItem(1).getCount()==8,"Full wallet partially sold deposits");
        h.assertTrue(manager.getBalance(wallet)==MoneyMath.MAX_MONEY-1&&manager.ledger(wallet).isEmpty()&&manager.saleRemainder(wallet)==0,"Rejected sale mutated accounting");h.succeed();
    }

    @GameTest(template="empty")
    public static void modPricesAndLegacyRulesCannotRestoreForbiddenTrades(GameTestHelper h) {
        var p=ProgressionTest.player(h,"mod-disabled");var prices=new PriceService();
        prices.basePrices().put("example:advanced_circuit",1_000_000);
        h.assertTrue(!prices.resolve("example:advanced_circuit").known()&&!prices.snapshot().containsKey("example:advanced_circuit"),"Configured price bypassed vanilla policy");
        h.assertTrue(new RecipePricer(prices).computeUnit("example:advanced_circuit",new HashSet<>())==0,"Recipe graph restored a mod price");
        var rules=new PurchaseRules(p.getServer());
        h.assertTrue(!rules.item(p,"example:advanced_circuit").unlocked()&&!rules.enchantment(p,"example:enchantment").unlocked(),"Progression bypassed mod policy");
        var economy=new EconomyService(p.getServer(),new TeamEconomyManager(),TeamEconomyMod.get().prices());
        var machine=new ItemStack(ModRegistries.SHOP_MACHINE.get());
        h.assertTrue(economy.sell(p,machine).outcome()==EconomyService.Outcome.NOT_PRICED&&!machine.isEmpty(),"Mod equipment was recycled");h.succeed();
    }

    @GameTest(template="empty")
    public static void blindBatchesIgnoreStagesChargeExactlyAndDeliverEveryPrize(GameTestHelper h) throws Exception {
        var p=ProgressionTest.player(h,"batch-box");var manager=new TeamEconomyManager();
        var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());manager.setBalance(wallet,100_000);
        Path dir=Files.createTempDirectory("teamecon-batch-box-");
        try {
            Files.writeString(dir.resolve("teamecon_blindbox.json"),"""
                [{"id":"diamond","price":1000,"stage":"never_unlocked","entries":[{"item":"minecraft:diamond","count":1,"weight":1}]}]
                """);
            var shop=new ShopService(p.getServer(),manager,TeamEconomyMod.get().prices());shop.loadConfigs(dir);
            var first=shop.buyBlindBox(p,"diamond",64);
            h.assertTrue(first.outcome()==ShopService.Outcome.OK&&first.charged()==64_000&&first.rewards().equals("minecraft:diamond,64"),"Batch still locked or receipt is wrong");
            h.assertTrue(manager.getBalance(wallet)==36_000&&p.getInventory().countItem(Items.DIAMOND)==64,"Batch charge or delivery was incomplete");
            for(int invalid:new int[]{0,-1,65,Integer.MAX_VALUE})h.assertTrue(shop.buyBlindBox(p,"diamond",invalid).outcome()==ShopService.Outcome.BAD_ITEM,"Invalid batch accepted");
            h.assertTrue(shop.buyBlindBox(p,"diamond",64).outcome()==ShopService.Outcome.NO_FUNDS&&manager.getBalance(wallet)==36_000,"Unfunded batch charged");
        } finally {try(var files=Files.list(dir)){for(Path file:files.toList())Files.delete(file);}Files.delete(dir);}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void mixedBoxBatchesReserveSpaceForEveryCombination(GameTestHelper h) {
        var p=ProgressionTest.player(h,"batch-space");
        for(int i=0;i<36;i++)p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().items.set(0,ItemStack.EMPTY);
        var outcomes=List.of(new ItemStack(Items.DIAMOND,32),new ItemStack(Items.GOLD_INGOT,32));
        h.assertTrue(ItemDelivery.canFit(p.getInventory(),outcomes.getFirst())&&ItemDelivery.canFit(p.getInventory(),outcomes.getLast()),"Test requires individual prizes to fit");
        h.assertTrue(!ItemDelivery.canFitAnyBatch(p.getInventory(),outcomes,4),"Mixed prize batch can overflow one free slot");
        p.getInventory().items.set(1,ItemStack.EMPTY);p.getInventory().items.set(2,ItemStack.EMPTY);
        h.assertTrue(ItemDelivery.canFitAnyBatch(p.getInventory(),outcomes,4),"Safe three-slot batch was rejected");
        h.assertTrue(ItemDelivery.canFitAnyBatch(p.getInventory(),List.of(ItemStack.EMPTY),64),"Empty boxes require inventory slots");h.succeed();
    }

    @GameTest(template="empty")
    public static void wagerFactorsCanBeLoweredAfterReopeningAndPersistToDisk(GameTestHelper h) {
        var p=ProgressionTest.player(h,"factor-edit");var level=h.getLevel();
        var origin=h.absolutePos(new BlockPos(3,1,3));var block=ModRegistries.SLOT_MACHINE.get();
        var state=block.defaultBlockState().setValue(CasinoMachineBlock.FACING,Direction.SOUTH).setValue(CasinoMachineBlock.ASSEMBLED,true);
        level.setBlock(origin,state,3);block.setPlacedBy(level,origin,state,p,new ItemStack(block));
        var machine=(CasinoMachineBlockEntity)level.getBlockEntity(origin.above());p.setPos(origin.getX()+1,origin.getY(),origin.getZ()+2);
        var manager=TeamEconomyMod.get().economy().manager();var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
        CasinoLevelsTest.unlock(manager,wallet,5);machine.select(p,100,"");
        long cap=TeamEconomyMod.get().gambling().maxBet("slots",wallet);
        var first=new MachineBetMenu(82,p.getInventory(),machine.getBlockPos(),machine.betBase(),cap,machine.betFactor());
        h.assertTrue(first.apply(p,10)&&machine.bet()==1000,"Initial factor failed");
        var second=new MachineBetMenu(83,p.getInventory(),machine.getBlockPos(),machine.betBase(),cap,machine.betFactor());
        h.assertTrue(second.factor()==10&&second.apply(p,2)&&machine.bet()==200&&machine.betBase()==100,"Reopened factor compounded the stake");
        var copy=new CasinoMachineBlockEntity(machine.getBlockPos(),machine.getBlockState());
        copy.loadWithComponents(machine.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
        h.assertTrue(copy.betBase()==100&&copy.betFactor()==2&&copy.bet()==200,"Saved factor lost its base");
        var third=new MachineBetMenu(84,p.getInventory(),machine.getBlockPos(),machine.betBase(),cap,machine.betFactor());
        h.assertTrue(third.apply(p,1)&&machine.bet()==100,"Factor one did not restore the base");
        var stale=new MachineBetMenu(85,p.getInventory(),machine.getBlockPos(),machine.betBase(),cap,machine.betFactor());
        machine.select(p,300,"");h.assertTrue(!stale.apply(p,2)&&machine.bet()==300,"Stale factor menu overwrote a new stake");h.succeed();
    }

    @GameTest(template="empty")
    public static void physicalPanelHitboxesFollowEachLayout(GameTestHelper h) {
        for(var block:List.of(ModRegistries.SLOT_MACHINE.get(),ModRegistries.ROULETTE_TABLE.get(),ModRegistries.COLOR_WHEEL_TABLE.get(),ModRegistries.PENGUIN_MACHINE.get(),ModRegistries.MULTIPLIER_MACHINE.get(),ModRegistries.HILO_TABLE.get())) {
            for(Direction facing:Direction.Plane.HORIZONTAL) {
                var state=block.defaultBlockState().setValue(CasinoMachineBlock.FACING,facing).setValue(CasinoMachineBlock.ASSEMBLED,true);
                var machine=new CasinoMachineBlockEntity(BlockPos.ZERO.above(),state);
                double offset=MachineLayout.controlsOffset(block.game());
                var right=facing.getCounterClockWise();var back=facing.getOpposite();
                boolean roulette=block.game()==GameType.ROULETTE;
                for(var target:Map.of(roulette?1.14:1.4,MachineInteraction.Control.START,roulette?1.4:1.14,MachineInteraction.Control.NONE,.64,MachineInteraction.Control.MINUS_100).entrySet()) {
                    double x=.28,y=target.getKey()+offset;
                    var hit=new Vec3(.5+right.getStepX()*(x-.5)-back.getStepX()*.5,y,.5+right.getStepZ()*(x-.5)-back.getStepZ()*.5);
                    h.assertTrue(MachineInteraction.controlAt(machine,new BlockHitResult(hit,facing,BlockPos.containing(hit),false))==target.getValue(),"Wrong panel order: "+block.game()+" / "+facing);
                }
            }
        }
        h.succeed();
    }
}
