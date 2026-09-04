package com.moonlightc.limen.block;

import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
    /** Tooltip lines before the "and N more" summary, matching the shulker box's own limit. */
    private static final int TOOLTIP_ITEM_LIMIT = 5;

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

    /**
     * Drops the box with its contents even in creative, the way a shulker box does. Creative breaking
     * normally skips the loot table entirely, which for a container would destroy whatever was inside;
     * a non-empty box is spawned by hand here instead, before the block is removed.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ArtisanBoxBlockEntity box) {
            if (!level.isClientSide && player.isCreative() && !box.isEmpty()) {
                ItemStack stack = new ItemStack(this);
                box.saveToItem(stack);
                if (box.hasCustomName()) {
                    stack.setHoverName(box.getCustomName());
                }

                ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D, stack);
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            } else {
                // Survival breaking goes through the loot table, which needs the loot seed resolved
                // first so the contents that end up on the item are the ones that were generated.
                box.unpackLootTable(player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Lists the stored contents on the item tooltip, the way a shulker box does: up to five entries
     * plus a summary line for the rest.
     */
    @Override
    public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        CompoundTag blockEntityTag = BlockItem.getBlockEntityData(stack);
        if (blockEntityTag == null) {
            return;
        }

        if (blockEntityTag.contains("LootTable", Tag.TAG_STRING)) {
            tooltip.add(Component.literal("???????"));
        }
        if (!blockEntityTag.contains(ITEMS_TAG, Tag.TAG_LIST)) {
            return;
        }

        NonNullList<ItemStack> contents =
                NonNullList.withSize(ArtisanBoxBlockEntity.CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(blockEntityTag, contents);

        int shown = 0;
        int total = 0;
        for (ItemStack content : contents) {
            if (content.isEmpty()) {
                continue;
            }
            total++;
            if (shown < TOOLTIP_ITEM_LIMIT) {
                shown++;
                tooltip.add(content.getHoverName().copy().append(" x").append(String.valueOf(content.getCount())));
            }
        }

        if (total > shown) {
            tooltip.add(Component.translatable("container.shulkerBox.more", total - shown)
                    .withStyle(ChatFormatting.ITALIC));
        }
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
