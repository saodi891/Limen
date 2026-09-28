package com.moonlightc.limen.block;

import com.moonlightc.limen.inventory.TableSawMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class TableSawBlock extends StonecutterBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty REVERSED = BlockStateProperties.INVERTED;
    private static final Component CONTAINER_TITLE = Component.translatable("container.limen.table_saw");

    public TableSawBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(REVERSED, false));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean moving) {
        if (!level.isClientSide && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moving);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.is(this)) {
            return;
        }

        Direction facing = state.getValue(FACING);
        boolean powered = state.getValue(POWERED);
        boolean reversed = state.getValue(REVERSED);
        if (powered) {
            Direction inputDirection = reversed ? facing.getOpposite() : facing;
            if (!this.hasInputFrom(level, pos, inputDirection)) {
                level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
                level.scheduleTick(pos, this, 1);
            }
            return;
        }

        boolean frontPowered = this.hasInputFrom(level, pos, facing);
        boolean backPowered = this.hasInputFrom(level, pos, facing.getOpposite());
        if (frontPowered || backPowered) {
            if (frontPowered != backPowered) {
                reversed = backPowered;
            }
            level.setBlock(pos, state.setValue(POWERED, true)
                    .setValue(REVERSED, reversed), Block.UPDATE_ALL);
        }
    }

    private boolean hasInputFrom(Level level, BlockPos pos, Direction direction) {
        BlockPos sourcePos = pos.relative(direction);
        BlockState sourceState = level.getBlockState(sourcePos);
        int signal = level.getSignal(sourcePos, direction);
        if (sourceState.is(Blocks.REDSTONE_WIRE)) {
            signal = Math.max(signal, sourceState.getValue(RedStoneWireBlock.POWER));
        }
        return signal > 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(POWERED)) {
            return 0;
        }
        Direction inputDirection = state.getValue(REVERSED)
                ? state.getValue(FACING).getOpposite()
                : state.getValue(FACING);
        return direction == inputDirection.getOpposite() ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(POWERED)) {
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = this.getMenuProvider(state, level, pos);
            if (provider != null) {
                NetworkHooks.openScreen(serverPlayer, provider, pos);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(new net.minecraft.world.inventory.MenuConstructor() {
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return new TableSawMenu(containerId, inventory, ContainerLevelAccess.create(level, pos));
            }
        }, CONTAINER_TITLE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, REVERSED);
    }
}
