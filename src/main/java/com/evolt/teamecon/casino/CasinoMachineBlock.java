package com.evolt.teamecon.casino;

import com.evolt.teamecon.init.ModRegistries;
import com.evolt.teamecon.network.ModNetwork;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One placeable game machine, two blocks tall: a decorative pedestal below and the cabinet
 * with the mechanism above. Only the upper half holds the block entity; either half opens the
 * interface, and breaking either half takes the whole machine with a single drop. The model
 * and the renderer are specific to the game, so a slot machine actually looks like a slot
 * machine, and the facing property keeps the front towards the player who placed it.
 */
public class CasinoMachineBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Upper half carries the mechanism, lower half is the pedestal; the pair is one machine. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Half> HALF =
            BlockStateProperties.HALF;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ASSEMBLED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("assembled");

    private final GameType game;

    public CasinoMachineBlock(Properties properties, GameType game) {
        super(properties);
        this.game = game;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM).setValue(ASSEMBLED,false));
    }

    public GameType game() {
        return game;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        if(!MachineStructure.canPlace(context,this))return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(ASSEMBLED,true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        if(state.getValue(ASSEMBLED))MachineStructure.assemble(level,pos,state);
        else level.setBlock(pos.above(), state.setValue(HALF, Half.TOP), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        Direction partner = state.getValue(HALF) == Half.BOTTOM ? Direction.UP : Direction.DOWN;
        if (direction == partner && (neighbour.getBlock() != this
                || neighbour.getValue(HALF) == state.getValue(HALF)
                || neighbour.getValue(FACING) != state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        if(state.getValue(ASSEMBLED))return MachineLayout.of(game).table()
                ?MachineLayout.rouletteShape(0,state.getValue(HALF)==Half.TOP?1:0,0):net.minecraft.world.phys.shapes.Shapes.block();
        return state.getValue(HALF) == Half.BOTTOM ? Block.box(2, 0, 2, 14, 16, 14) : Block.box(0, 0, 0, 16, 16, 16);
    }

    @Override
    protected MapCodec<? extends CasinoMachineBlock> codec() {
        return CODEC;
    }

    // Statically registered blocks are never decoded from data, so the per-registration game
    // constant only needs a placeholder; real instances always come from the block registry.
    public static final MapCodec<CasinoMachineBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BlockBehaviour.propertiesCodec()
            ).apply(instance, props -> new CasinoMachineBlock(props, GameType.SLOTS)));

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, ASSEMBLED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) != Half.TOP) {
            return null;
        }
        return ModRegistries.CASINO_MACHINE_ENTITY.value().create(pos, state);
    }

    /** The half that owns the block entity and the menu anchor. */
    public static BlockPos masterOf(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == Half.TOP ? pos : pos.above();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                           Player player, BlockHitResult hit) {
        if(state.getValue(ASSEMBLED)) {
            MachineInteraction.use(level,masterOf(pos,state),player,hit);
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos master = masterOf(pos, state);
        if (level.getBlockEntity(master) instanceof CasinoMachineBlockEntity entity) {
            player.openMenu(entity, buffer -> {
                buffer.writeUtf(game.id(), 32);
                buffer.writeBoolean(false);
                buffer.writeBlockPos(master);
            });
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendOpenSync(serverPlayer);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                      Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = useWithoutItem(state, level, pos, player, hit);
        return result.consumesAction() ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Breaking one half removes the whole machine; the lower half carries the single drop. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if(state.getValue(ASSEMBLED)) {
            MachineStructure.playerBreak(level,pos,state,player);
            return super.playerWillDestroy(level,pos,state,player);
        }
        if (!level.isClientSide) {
            Half half = state.getValue(HALF);
            BlockPos partner = half == Half.BOTTOM ? pos.above() : pos.below();
            BlockState partnerState = level.getBlockState(partner);
            if (partnerState.getBlock() == this) {
                if (player.getAbilities().instabuild) {
                    level.setBlock(partner, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (half == Half.TOP) {
                    // The drop lives on the lower half, so destroy it with drops.
                    level.destroyBlock(partner, true, player);
                } else {
                    level.setBlock(partner, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
        return state;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(state.getValue(ASSEMBLED)&&state.getBlock()!=next.getBlock())MachineStructure.removed(level,pos,state);
        super.onRemove(state,level,pos,next,moving);
    }

    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (l,p,s,entity) -> {
            if(entity instanceof CasinoMachineBlockEntity machine)MachineInteraction.tick(machine);
        };
    }

}
