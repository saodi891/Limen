package com.moonlightc.limen.item;

import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    /** Revalidating every tick would chase block entities constantly; once a second is plenty. */
    private static final int VALIDATE_INTERVAL_TICKS = 20;

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
}
