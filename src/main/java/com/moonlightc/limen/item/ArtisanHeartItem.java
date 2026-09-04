package com.moonlightc.limen.item;

import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Artisan heart. Right-clicking an artisan box binds this item to that box instead of opening it,
 * mirroring how a compass binds to a lodestone: the target position and dimension are stored on the
 * stack, and a bound heart glows with the enchantment foil.
 *
 * <p>Binding is strictly one-to-one. Each heart carries a unique {@link #TAG_HEART_ID}, and the box
 * remembers only the id of its current heart. Binding a second heart to the same box overwrites that
 * id, so the previous heart's id no longer matches; it notices during {@link #inventoryTick} and
 * clears itself, losing both the glint and the stored position.
 */
public class ArtisanHeartItem extends Item {
    public static final String TAG_BOX_POS = "ArtisanBoxPos";
    public static final String TAG_BOX_DIMENSION = "ArtisanBoxDimension";
    public static final String TAG_BOUND = "ArtisanBoxBound";
    /** Unique per binding, so a box can tell its current heart from one it has replaced. */
    public static final String TAG_HEART_ID = "ArtisanHeartId";
    /**
     * Snapshot of the artisan hotbar slots, kept current while the heart is carried so the contents
     * are still readable once it is not. Written under the vanilla {@code Items} key so
     * {@link net.minecraft.world.ContainerHelper} can load it directly.
     */
    public static final String TAG_STORED = "ArtisanStored";

    /** Revalidating every tick would chase block entities constantly; once a second is plenty. */
    private static final int VALIDATE_INTERVAL_TICKS = 20;
    /** Tooltip lines before the "and N more" summary, matching the shulker box's own limit. */
    private static final int TOOLTIP_ITEM_LIMIT = 5;

    public ArtisanHeartItem(Properties properties) {
        super(properties);
    }

    public static boolean isBound(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_BOUND);
    }

    /** This heart's binding id, or null if unbound. */
    @Nullable
    public static UUID getHeartId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_BOUND) || !tag.hasUUID(TAG_HEART_ID)) {
            return null;
        }
        return tag.getUUID(TAG_HEART_ID);
    }

    /** The bound box's position and dimension, or empty if this heart is unbound. */
    public static Optional<GlobalPos> getBoundBox(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_BOUND) || !tag.contains(TAG_BOX_POS)) {
            return Optional.empty();
        }

        Optional<ResourceKey<Level>> dimension = getBoundDimension(tag);
        if (dimension.isEmpty()) {
            return Optional.empty();
        }

        BlockPos pos = net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound(TAG_BOX_POS));
        return Optional.of(GlobalPos.of(dimension.get(), pos));
    }

    private static Optional<ResourceKey<Level>> getBoundDimension(CompoundTag tag) {
        return Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_BOX_DIMENSION)).result();
    }

    /**
     * Binds before the normal block interaction runs, so the box's own right-click (which opens the
     * container) never fires while a heart is held.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ArtisanBoxBlockEntity box)) {
            return super.useOn(context);
        }

        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            UUID heartId = UUID.randomUUID();
            bind(stack, level, pos, heartId);
            // Overwriting the box's id is what unbinds whichever heart was bound before.
            box.bindHeart(heartId);
            level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void bind(ItemStack stack, Level level, BlockPos pos, UUID heartId) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_BOX_POS, net.minecraft.nbt.NbtUtils.writeBlockPos(pos));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, level.dimension())
                .resultOrPartial(error -> {
                })
                .ifPresent(dimensionTag -> tag.put(TAG_BOX_DIMENSION, dimensionTag));
        tag.putUUID(TAG_HEART_ID, heartId);
        tag.putBoolean(TAG_BOUND, true);
    }

    private static void unbind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(TAG_BOX_POS);
        tag.remove(TAG_BOX_DIMENSION);
        tag.remove(TAG_HEART_ID);
        tag.remove(TAG_BOUND);
    }

    // ------------------------------------------------------- stored hotbar slots

    /**
     * Records the artisan hotbar slots on the heart. Called while the heart is carried, so that once
     * it leaves the inventory — locking the slots — the contents are still visible on the item.
     *
     * <p>Deliberately writes nothing when the slots are all empty, so an unused heart carries no tag
     * and stacks with a fresh one.
     */
    public static void setStoredSlots(ItemStack stack, NonNullList<ItemStack> slots) {
        boolean empty = true;
        for (ItemStack slot : slots) {
            if (!slot.isEmpty()) {
                empty = false;
                break;
            }
        }

        if (empty) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(TAG_STORED);
            }
            return;
        }
        stack.getOrCreateTag().put(TAG_STORED, ContainerHelper.saveAllItems(new CompoundTag(), slots, false));
    }

    /** The slots recorded by {@link #setStoredSlots}, or all-empty when the heart has never held any. */
    public static NonNullList<ItemStack> getStoredSlots(ItemStack stack, int size) {
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_STORED, Tag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(tag.getCompound(TAG_STORED), slots);
        }
        return slots;
    }

    /**
     * Drops the binding once it no longer holds: the box was broken, or another heart took its place.
     * Only checks while the bound box's chunk is loaded, so carrying a heart away from its box —
     * or into another dimension — leaves the binding intact.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % VALIDATE_INTERVAL_TICKS != 0) {
            return;
        }

        UUID heartId = getHeartId(stack);
        if (heartId == null) {
            return;
        }

        Optional<GlobalPos> bound = getBoundBox(stack);
        if (bound.isEmpty()) {
            unbind(stack);
            return;
        }

        GlobalPos target = bound.get();
        if (!target.dimension().equals(level.dimension()) || !level.isLoaded(target.pos())) {
            return;
        }

        if (!(level.getBlockEntity(target.pos()) instanceof ArtisanBoxBlockEntity box)
                || !heartId.equals(box.getHeartId())) {
            unbind(stack);
        }
    }

    /** Bound hearts shimmer, the same cue a lodestone compass uses. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isBound(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        appendStoredSlots(stack, tooltip);

        Optional<GlobalPos> bound = getBoundBox(stack);
        if (bound.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.limen.artisan_heart.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }

        BlockPos pos = bound.get().pos();
        ResourceLocation dimension = bound.get().dimension().location();
        tooltip.add(Component.translatable("tooltip.limen.artisan_heart.bound",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(dimension.toString()).withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Lists the stored artisan slots the way a shulker box lists its contents: up to
     * {@link #TOOLTIP_ITEM_LIMIT} entries as "name x count", then a summary line for the rest.
     */
    private static void appendStoredSlots(ItemStack stack, List<Component> tooltip) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_STORED, Tag.TAG_COMPOUND)) {
            return;
        }

        NonNullList<ItemStack> slots = getStoredSlots(stack, ArtisanHotbars.SLOT_COUNT);
        int shown = 0;
        int total = 0;
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) {
                continue;
            }
            total++;
            if (shown < TOOLTIP_ITEM_LIMIT) {
                shown++;
                tooltip.add(slot.getHoverName().copy().append(" x").append(String.valueOf(slot.getCount())));
            }
        }

        if (total > shown) {
            tooltip.add(Component.translatable("container.shulkerBox.more", total - shown)
                    .withStyle(ChatFormatting.ITALIC));
        }
    }
}
