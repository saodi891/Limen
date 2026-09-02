package com.moonlightc.limen.block;

import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Artisan box: a shulker-box-style container that keeps its contents when broken.
 *
 * <p>Unlike a shulker box it has no facing state — the lid always opens upward, so the model is
 * never rotated and the box reads the same however it was placed.
 */
public class ArtisanBoxBlock extends BaseEntityBlock {
    /** The lid always opens upward; there is no facing block state. */
    public static final Direction LID_DIRECTION = Direction.UP;
    public static final String ITEMS_TAG = "Items";

    public ArtisanBoxBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArtisanBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, com.moonlightc.limen.registry.ModBlockEntities.ARTISAN_BOX.get(),
                (tickLevel, pos, tickState, box) -> ArtisanBoxBlockEntity.tick(tickLevel, pos, tickState, box));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Rendered by the block entity renderer so the lid can animate.
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos,
                                                     net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand,
                                                     net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (player.isSpectator()) {
            return net.minecraft.world.InteractionResult.CONSUME;
        }

        if (level.getBlockEntity(pos) instanceof ArtisanBoxBlockEntity box && canOpen(level, pos, box)) {
            player.openMenu(box);
            player.awardStat(net.minecraft.stats.Stats.OPEN_SHULKER_BOX);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    /**
     * The lid needs free space above to swing open. Blocks, shulkers and boats all block it — see
     * {@link ArtisanBoxBlockEntity#canLidOpen}.
     */
    private static boolean canOpen(Level level, BlockPos pos, ArtisanBoxBlockEntity box) {
        if (!box.isClosed()) {
            return true;
        }
        return ArtisanBoxBlockEntity.canLidOpen(level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof ArtisanBoxBlockEntity box) {
            return box.isClosed() ? Shapes.block() : Shapes.create(box.getBoundingBox());
        }
        return Shapes.block();
    }

    /** Contents travel with the dropped item, like a shulker box. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof ArtisanBoxBlockEntity box) {
            params = params.withDynamicDrop(net.minecraft.world.level.block.ShulkerBoxBlock.CONTENTS, output -> {
                for (int slot = 0; slot < box.getContainerSize(); slot++) {
                    output.accept(box.getItem(slot));
                }
            });
        }
        return super.getDrops(state, params);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof ArtisanBoxBlockEntity box) {
            CompoundTag blockEntityTag = box.saveWithoutMetadata();
            if (!blockEntityTag.isEmpty()) {
                stack.addTagElement("BlockEntityTag", blockEntityTag);
            }
        }
        return stack;
    }
}
