package com.evolt.teamecon.casino;

import com.evolt.teamecon.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** One real item owns the entire footprint; extension blocks never have their own drops. */
public final class MachineStructure {
    private static final ThreadLocal<Boolean> CHANGING=ThreadLocal.withInitial(()->false);
    private MachineStructure(){}
    public static boolean canPlace(BlockPlaceContext context,CasinoMachineBlock machine) {
        var layout=MachineLayout.of(machine.game());var level=context.getLevel();var base=context.getClickedPos();
        Direction facing=context.getHorizontalDirection().getOpposite();
        if(base.getY()+layout.height()>level.getMaxBuildHeight())return false;
        for(int x=0;x<layout.width();x++)for(int y=0;y<layout.height();y++)for(int z=0;z<layout.depth();z++){
            BlockPos pos=MachineLayout.at(base,facing,x,y,z);
            if(!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos).canBeReplaced(context))return false;
        }
        return true;
    }
    public static void assemble(Level level,BlockPos base,BlockState bottom) {
        CHANGING.set(true);
        try {
            level.setBlock(base.above(),bottom.setValue(CasinoMachineBlock.HALF,Half.TOP),Block.UPDATE_ALL);
            var layout=MachineLayout.of(((CasinoMachineBlock)bottom.getBlock()).game());
            Direction facing=bottom.getValue(CasinoMachineBlock.FACING);
            for(int x=0;x<layout.width();x++)for(int y=0;y<layout.height();y++)for(int z=0;z<layout.depth();z++){
                if(x==0&&z==0&&y<2)continue;
                level.setBlock(MachineLayout.at(base,facing,x,y,z),ModRegistries.MACHINE_PART.get().defaultBlockState()
                        .setValue(MachinePartBlock.FACING,facing).setValue(MachinePartBlock.PART,MachineLayout.part(x,y,z)),Block.UPDATE_ALL);
            }
        } finally { CHANGING.set(false); }
    }
    public static BlockPos base(BlockPos pos,BlockState state) {
        if(state.getBlock() instanceof CasinoMachineBlock)return state.getValue(CasinoMachineBlock.HALF)==Half.TOP?pos.below():pos;
        return MachineLayout.origin(pos,state.getValue(MachinePartBlock.FACING),state.getValue(MachinePartBlock.PART));
    }
    public static void playerBreak(Level level,BlockPos pos,BlockState state,Player player) {
        if(level.isClientSide||CHANGING.get())return;
        BlockPos base=base(pos,state);BlockState root=level.getBlockState(base);
        if(!(root.getBlock() instanceof CasinoMachineBlock))return;
        clear(level,base,root,pos,!pos.equals(base)&&!player.getAbilities().instabuild,player);
    }
    public static void removed(Level level,BlockPos pos,BlockState oldState) {
        if(level.isClientSide||CHANGING.get())return;
        BlockPos base=base(pos,oldState);
        BlockState root=pos.equals(base)?oldState:level.getBlockState(base);
        if(!(root.getBlock() instanceof CasinoMachineBlock)||!root.getValue(CasinoMachineBlock.ASSEMBLED))return;
        clear(level,base,root,pos,!pos.equals(base),null);
    }
    private static void clear(Level level,BlockPos base,BlockState root,BlockPos clicked,boolean dropBase,Player player) {
        CHANGING.set(true);
        try {
            var layout=MachineLayout.of(((CasinoMachineBlock)root.getBlock()).game());
            Direction facing=root.getValue(CasinoMachineBlock.FACING);
            if(dropBase)level.destroyBlock(base,true,player);
            for(int x=0;x<layout.width();x++)for(int y=0;y<layout.height();y++)for(int z=0;z<layout.depth();z++){
                BlockPos pos=MachineLayout.at(base,facing,x,y,z);if(pos.equals(clicked))continue;
                BlockState state=level.getBlockState(pos);
                boolean ours=state.getBlock()==root.getBlock() && x==0&&z==0&&y<2
                        || state.getBlock() instanceof MachinePartBlock && base.equals(base(pos,state));
                if(ours)level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
        } finally { CHANGING.set(false); }
    }
}
