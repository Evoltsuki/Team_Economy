package com.evolt.teamecon.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Dimensions in blocks. Origin is the lower front-left block as seen by the operator. */
public record MachineLayout(int width,int height,int depth,boolean table) {
    public static float controlsOffset(GameType game) {
        return game == GameType.COLOR_WHEEL ? -.50F : game == GameType.ROULETTE ? -.42F : 0;
    }
    /** Shape of an assembled roulette cell; the reserved 3x3x3 structure stays unchanged. */
    public static net.minecraft.world.phys.shapes.VoxelShape rouletteShape(int x,int y,int z) {
        if(y==0)return net.minecraft.world.level.block.Block.box(0,0,0,16,z==0?13.5:15,16);
        if(y==1&&z==2)return net.minecraft.world.level.block.Block.box(0,0,0,16,8.5,16);
        if(y==1&&x==1&&z==1)return net.minecraft.world.level.block.Block.box(5,0,5,11,3,11);
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }
    public static MachineLayout of(GameType game) {
        return switch(game) {
            case COLOR_WHEEL -> new MachineLayout(3,3,3,false);
            case ROULETTE -> new MachineLayout(3,3,3,true);
            case PENGUIN -> new MachineLayout(3,3,2,false);
            default -> new MachineLayout(2,3,2,false);
        };
    }
    public static int part(int x,int y,int z){return x+y*3+z*9;}
    public static int x(int part){return part%3;}
    public static int y(int part){return part/3%3;}
    public static int z(int part){return part/9;}
    public static BlockPos at(BlockPos origin,Direction front,int x,int y,int z){
        return origin.relative(front.getCounterClockWise(),x).above(y).relative(front.getOpposite(),z);
    }
    public static BlockPos origin(BlockPos pos,Direction front,int part){return at(pos,front,-x(part),-y(part),-z(part));}
}
