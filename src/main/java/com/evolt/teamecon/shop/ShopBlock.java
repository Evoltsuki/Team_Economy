package com.evolt.teamecon.shop;

import com.evolt.teamecon.init.ModRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Two-block vending cabinet. Only the lower half owns the menu, animation and item drop. */
public class ShopBlock extends BaseEntityBlock {
    public static final MapCodec<ShopBlock> CODEC=simpleCodec(ShopBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF=BlockStateProperties.HALF;
    private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);
    public ShopBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,Half.BOTTOM));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,HALF);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        BlockPos top=c.getClickedPos().above();
        if(top.getY()>=c.getLevel().getMaxBuildHeight()||!c.getLevel().getWorldBorder().isWithinBounds(top)
                ||!c.getLevel().getBlockState(top).canBeReplaced(c))return null;
        return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){
        level.setBlock(pos.above(),state.setValue(HALF,Half.TOP),Block.UPDATE_ALL);
    }
    public static BlockPos base(BlockPos pos,BlockState state){return state.getValue(HALF)==Half.TOP?pos.below():pos;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(1,0,1,15,16,15);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer p)ShopMenu.open(p,base(pos,state),false,state.is(ModRegistries.BLIND_BOX_MACHINE.get())?"boxes":"sell");
        return InteractionResult.SUCCESS;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
        useWithoutItem(state,level,pos,p,hit);return ItemInteractionResult.SUCCESS;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(HALF)==Half.BOTTOM?new ShopMachineBlockEntity(pos,state):null;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        removePartner(level,pos,state,!player.getAbilities().instabuild,player);
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock())removePartner(level,pos,state,true,null);
        super.onRemove(state,level,pos,next,moving);
    }
    private void removePartner(Level level,BlockPos pos,BlockState state,boolean drop,Player player){
        if(level.isClientSide||REMOVING.get())return;
        BlockPos other=state.getValue(HALF)==Half.TOP?pos.below():pos.above();
        BlockState partner=level.getBlockState(other);
        if(!partner.is(this)||partner.getValue(HALF)==state.getValue(HALF)||partner.getValue(FACING)!=state.getValue(FACING))return;
        REMOVING.set(true);
        try {
            if(state.getValue(HALF)==Half.TOP&&drop)level.destroyBlock(other,true,player);
            else level.setBlock(other,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        }finally{REMOVING.set(false);}
    }
}
