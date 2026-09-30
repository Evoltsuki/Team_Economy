package com.evolt.teamecon.gametest;

import com.evolt.teamecon.casino.CasinoMachineBlock;
import com.evolt.teamecon.casino.CasinoMachineBlockEntity;
import com.evolt.teamecon.casino.MachineLayout;
import com.evolt.teamecon.casino.MachinePartBlock;
import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("teamecon")
public final class MachinePlacementTest {
    private MachinePlacementTest() {}
    private static Block[] machines() {
        return new Block[]{ModRegistries.SLOT_MACHINE.get(),ModRegistries.MULTIPLIER_MACHINE.get(),
                ModRegistries.COLOR_WHEEL_TABLE.get(),ModRegistries.HILO_TABLE.get(),ModRegistries.ROULETTE_TABLE.get(),
                ModRegistries.PENGUIN_MACHINE.get(),ModRegistries.SHOP_MACHINE.get()};
    }

    private static ItemStack place(GameTestHelper h, Block block, BlockPos relative, float yaw) {
        BlockPos pos=h.absolutePos(relative);
        h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        Player player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+4); player.setYRot(yaw);
        ItemStack stack=new ItemStack(block.asItem(),2);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
        ((BlockItem)block.asItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit));
        return stack;
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void placesBothHalvesAndLoadsAllRecipes(GameTestHelper h) {
        Block[] blocks=machines();
        for(int i=0;i<blocks.length;i++) {
            BlockPos relative=new BlockPos(4+i*5,1,5), pos=h.absolutePos(relative);
            ItemStack remaining=place(h,blocks[i],relative,(i%4)*90F);
            h.assertTrue(remaining.getCount()==1,"Placement must consume exactly one item");
            var state=h.getLevel().getBlockState(pos);
            h.assertTrue(state.is(blocks[i]),"Missing machine "+i);
            h.assertTrue(state.getRenderShape()==RenderShape.MODEL,"Machine must have a visible block model");
            if(blocks[i] instanceof CasinoMachineBlock machine) {
                h.assertTrue(state.getValue(CasinoMachineBlock.ASSEMBLED),"New placements must assemble the full footprint");
                var upper=h.getLevel().getBlockState(pos.above());
                h.assertTrue(upper.is(blocks[i])&&upper.getValue(CasinoMachineBlock.HALF)==Half.TOP,"Missing upper half");
                h.assertTrue(upper.getValue(CasinoMachineBlock.FACING)==state.getValue(CasinoMachineBlock.FACING),"Mismatched facing");
                h.assertTrue(h.getLevel().getBlockEntity(pos)==null,"Lower half must not own a second entity");
                h.assertTrue(h.getLevel().getBlockEntity(pos.above()) instanceof CasinoMachineBlockEntity,"Missing upper entity");
                var size=MachineLayout.of(machine.game());
                for(int x=0;x<size.width();x++)for(int y=0;y<size.height();y++)for(int z=0;z<size.depth();z++) {
                    if(x==0&&z==0&&y<2)continue;
                    var part=MachineLayout.at(pos,state.getValue(CasinoMachineBlock.FACING),x,y,z);
                    h.assertTrue(h.getLevel().getBlockState(part).getBlock() instanceof MachinePartBlock,"Unreserved machine cell");
                    h.assertTrue(h.getLevel().getBlockEntity(part)==null,"Extension has a second entity");
                }
            }
        }
        for(String name:new String[]{"slot_machine","multiplier_machine","color_wheel_table","hilo_table","roulette_table","penguin_machine","shop_machine"})
            h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("teamecon",name)).isPresent(),
                    "Minecraft 1.21 did not load recipe "+name);
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("teamecon","scratch_table")).isEmpty(),"Retired machine is still craftable");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("teamecon","terminal")).isEmpty(),"Late terminal still has a crafting recipe");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void ceilingPreventsHalfPlacement(GameTestHelper h) {
        BlockPos relative=new BlockPos(4,1,5),pos=h.absolutePos(relative);
        h.getLevel().setBlock(pos.above(),Blocks.STONE.defaultBlockState(),3);
        ItemStack remaining=place(h,ModRegistries.SLOT_MACHINE.get(),relative,0);
        h.assertTrue(remaining.getCount()==2,"Blocked placement consumed an item");
        h.assertTrue(h.getLevel().isEmptyBlock(pos),"Blocked placement left an orphaned half");
        h.succeed();
    }

    private static void breakHalf(GameTestHelper h, boolean top) {
        BlockPos relative=new BlockPos(4,1,5),pos=h.absolutePos(relative);
        Block block=ModRegistries.ROULETTE_TABLE.get();
        place(h,block,relative,0);
        h.getLevel().destroyBlock(top?pos.above():pos,true);
        h.runAfterDelay(2,()->{
            h.assertTrue(h.getLevel().isEmptyBlock(pos)&&h.getLevel().isEmptyBlock(pos.above()),"Breaking left an orphaned half");
            h.assertItemEntityCountIs(block.asItem(),relative,3,1);
            h.succeed();
        });
    }
    @GameTest(template="empty") public static void breakingTopDropsOnce(GameTestHelper h) { breakHalf(h,true); }
    @GameTest(template="empty") public static void breakingBottomDropsOnce(GameTestHelper h) { breakHalf(h,false); }

    @GameTest(template="empty",timeoutTicks=100)
    public static void animationUsesWorldTicksAndHasAnUpdatePacket(GameTestHelper h) {
        BlockPos relative=new BlockPos(4,1,5),pos=h.absolutePos(relative);
        place(h,ModRegistries.ROULETTE_TABLE.get(),relative,0);
        CasinoMachineBlockEntity entity=(CasinoMachineBlockEntity)h.getLevel().getBlockEntity(pos.above());
        entity.showSpin(new String[]{"roulette","7",""},true);
        h.assertTrue(entity.getUpdatePacket()!=null,"Nearby viewers need an update packet");
        h.assertTrue(entity.spinTicks()==64,"Roulette animation duration");
        h.runAfterDelay(66,()->{
            h.assertTrue(entity.spinTicks()==0,"Animation did not end without rendering frames");
            h.assertTrue(entity.winTicks()>0,"Win lights should follow the spin");
            h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void everyExtensionBlocksPlacementAndDismantlesOnce(GameTestHelper h) {
        var block=ModRegistries.COLOR_WHEEL_TABLE.get();var size=MachineLayout.of(block.game());
        BlockPos relative=new BlockPos(6,1,6),base=h.absolutePos(relative);
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"machine-break"));
        for(Direction front:Direction.Plane.HORIZONTAL)for(int x=0;x<size.width();x++)for(int y=0;y<size.height();y++)for(int z=0;z<size.depth();z++) {
            if(x==0&&y==0&&z==0)continue;
            BlockPos part=MachineLayout.at(base,front,x,y,z);
            h.getLevel().setBlock(part,Blocks.STONE.defaultBlockState(),3);
            var refused=place(h,block,relative,front.getOpposite().toYRot());
            h.assertTrue(refused.getCount()==2&&h.getLevel().isEmptyBlock(base),"Obstruction was overwritten or consumed machine");
            h.getLevel().setBlock(part,Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(place(h,block,relative,front.getOpposite().toYRot()).getCount()==1,"Could not place clear cabinet");
            player.setGameMode(GameType.SURVIVAL);
            h.assertTrue(player.gameMode.destroyBlock(part),"Survival could not break extension");
            assertFootprintGone(h,base,front,size);
            h.assertItemEntityCountIs(block.asItem(),relative,5,1);
            h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(base).inflate(5)).forEach(net.minecraft.world.entity.Entity::discard);
            h.assertTrue(place(h,block,relative,front.getOpposite().toYRot()).getCount()==1,"Cannot replace dismantled cabinet");
            player.setGameMode(GameType.CREATIVE);player.gameMode.destroyBlock(part);
            assertFootprintGone(h,base,front,size);
            h.assertItemEntityCountIs(block.asItem(),relative,5,0);
        }
        h.succeed();
    }
    private static void assertFootprintGone(GameTestHelper h,BlockPos base,Direction front,MachineLayout size){
        for(int x=0;x<size.width();x++)for(int y=0;y<size.height();y++)for(int z=0;z<size.depth();z++)
            h.assertTrue(h.getLevel().isEmptyBlock(MachineLayout.at(base,front,x,y,z)),"Dismantling left an invisible part");
    }
}
