package com.evolt.teamecon.gametest;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.economy.EconomyService;
import com.evolt.teamecon.economy.TeamEconomyManager;
import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.shop.ShopMenu;
import com.evolt.teamecon.team.TeamUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("teamecon")
public final class MarketFeedbackTest {
    @GameTest(template="empty")
    public static void goldBlockPreservesItsNineIngots(GameTestHelper h) {
        PriceService prices=TeamEconomyMod.get().prices();
        long ingot=prices.resolve("minecraft:gold_ingot").unitPrice();
        long block=prices.resolve("minecraft:gold_block").unitPrice();
        h.assertTrue(block==ingot*9,"Gold block="+block+", ingot="+ingot+"; compression must preserve material value");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void storageBlocksAndLooseMaterialsSettleEqually(GameTestHelper h) {
        var p=ProgressionTest.player(h,"storage-sale");
        var prices=TeamEconomyMod.get().prices();
        for (var pair : new net.minecraft.world.item.Item[][]{{Items.GOLD_BLOCK,Items.GOLD_INGOT},{Items.IRON_BLOCK,Items.IRON_INGOT},{Items.DIAMOND_BLOCK,Items.DIAMOND}}) {
            var blocks=new TeamEconomyManager(); var loose=new TeamEconomyManager();
            var first=new EconomyService(p.getServer(),blocks,prices);
            var second=new EconomyService(p.getServer(),loose,prices);
            long blockQuote=first.saleQuote(p,new ItemStack(pair[0],7));
            long looseQuote=second.saleQuote(p,new ItemStack(pair[1],63));
            h.assertTrue(blockQuote==looseQuote,"Compressed and loose quotes differ: "+pair[0]);
            var a=first.sell(p,new ItemStack(pair[0],7)); var b=second.sell(p,new ItemStack(pair[1],63));
            var wallet=TeamUtil.walletKey(p.getServer(),p.getUUID());
            h.assertTrue(a.outcome()==EconomyService.Outcome.OK && a.total()==b.total(),"Compressed sale changed payment");
            h.assertTrue(Math.abs(blocks.saleRemainder(wallet)-loose.saleRemainder(wallet))<1e-8,"Compressed sale changed fractional remainder");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void configuredStoragePricesStayConsistentAcrossRecipesAndSnapshots(GameTestHelper h) {
        PriceService prices=new PriceService();
        prices.basePrices().put("minecraft:gold_ingot",100);
        h.assertTrue(prices.resolve("minecraft:gold_block").unitPrice()==900,"Configured ingot did not set block value");
        h.assertTrue(new com.evolt.teamecon.price.RecipePricer(prices).computeUnit("minecraft:gold_block",new java.util.HashSet<>())==900,"Recipe traversal ignored material value");
        h.assertTrue(prices.snapshot().get("minecraft:gold_block")==900,"Snapshot lost storage value");
        prices.basePrices().put("minecraft:gold_block",750);
        h.assertTrue(prices.resolve("minecraft:gold_block").unitPrice()==750 && prices.snapshot().get("minecraft:gold_block")==750,"Explicit block override was ignored");
        PriceService disabled=new PriceService(); disabled.basePrices().put("minecraft:gold_ingot",0);
        h.assertTrue(!disabled.resolve("minecraft:gold_block").known()&&!disabled.snapshot().containsKey("minecraft:gold_block"),"Disabled material returned through a block fallback");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void vendingOpensSellWhileBlindBoxKeepsItsOwnPage(GameTestHelper h) {
        var p=ProgressionTest.player(h,"default-tab");
        for (boolean box : new boolean[]{false,true}) {
            var block=box?ModRegistries.BLIND_BOX_MACHINE.get():ModRegistries.SHOP_MACHINE.get();
            var pos=h.absolutePos(new BlockPos(box?5:2,2,2));
            h.getLevel().setBlock(pos,block.defaultBlockState(),3);
            h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(),p,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
            h.assertTrue(p.containerMenu instanceof ShopMenu && ((ShopMenu)p.containerMenu).initialTab().equals(box?"boxes":"sell"),"Cabinet opened the wrong page");
            p.closeContainer();
        }
        h.succeed();
    }
}
