package com.moonlightc.limen.block.entity;

import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.inventory.ArtisanBoxMenu;
import com.moonlightc.limen.item.ArtisanHeartItem;
import com.moonlightc.limen.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Artisan box. Behaves like a vanilla shulker box (27 slots, contents survive being broken), but the
 * lid only ever opens upward and its animation comes from a Blockbench model.
 *
 * <p>An artisan box can be bound to a single {@link ArtisanHeartItem}. While that heart sits in a
 * player's inventory within {@link LimenCommonConfig#ARTISAN_BOX_LINK_DISTANCE} Manhattan blocks and
 * the lid has room to open, the box enters its <em>linked</em> state: the lid holds open with nobody
 * viewing, and the box's owner becomes that player.
 *
 * <p>The lid is driven by a single synced {@link #lidOpen} flag rather than by block events, so
 * viewer-driven and link-driven opening share one path and the client never has to guess.
 *
 * <p>The first {@link #LINKED_SLOTS} slots are reserved for behaviour landing in the artisan heart
 * update; {@link #isLinkedSlot(int)} already marks them so that behaviour can hook in without
 * reshaping the container.
 */
public class ArtisanBoxBlockEntity extends RandomizableContainerBlockEntity implements GeoBlockEntity {
    public static final int CONTAINER_SIZE = 27;
    /** Slots reserved for the linked-state behaviour shipping in the artisan heart update. */
    public static final int LINKED_SLOTS = 9;
    private static final int OPEN_ANIMATION_TICKS = 10;
    /**
     * How stale an open lid may be and still animate for a client that is only now seeing this box.
     * Sized to cover the server having flipped the lid while the chunk was still queued for sending —
     * which is what running at speed does — without letting a box that has stood open for a while
     * replay its animation for every player who later wanders up to it.
     */
    private static final int LID_ANIMATE_ON_SIGHT_TICKS = 100;
    /** The lid always opens upward, and needs this much clear space above to do so. */
    public static final double LID_CLEARANCE = 0.5D;

    public static final String TAG_HEART_ID = "ArtisanHeartId";
    public static final String TAG_OWNER_ID = "ArtisanBoxOwner";
    public static final String TAG_LID_OPEN = "ArtisanBoxLidOpen";
    /** Only ever written into sync tags, never to disk — see {@link #adoptLidState(CompoundTag)}. */
    public static final String TAG_LID_AGE = "ArtisanBoxLidAge";

    public static final String CONTROLLER_LID = "lid";
    // Animation names come from the Blockbench file: plain "open" / "close", held on the last frame
    // so the lid stays up for as long as the box is open or linked.
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("close");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private int openCount;
    private AnimationStatus animationStatus = AnimationStatus.CLOSED;
    private float progress;
    private float progressOld;

    /** Whether the lid should be up. Synced; the animation on both sides follows it. */
    private boolean lidOpen;
    /** Cleared on the first sync so a box that is already open does not animate open again. */
    private boolean adoptedLidState;
    /**
     * Server-side game time of the last {@link #lidOpen} flip, or {@link Long#MIN_VALUE} for a box
     * loaded from disk. Sent as an age so a client meeting this box for the first time can tell a lid
     * that just moved from one that was already up before it arrived.
     */
    private long lidChangedAt = Long.MIN_VALUE;

    /** Identity of the bound heart, so a heart that has since been re-bound elsewhere stops matching. */
    @Nullable
    private UUID heartId;
    /** Player currently holding the bound heart in range. Null whenever the box is not linked. */
    @Nullable
    private UUID ownerId;

    public ArtisanBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARTISAN_BOX.get(), pos, state);
    }

    public enum AnimationStatus {
        CLOSED,
        OPENING,
        OPENED,
        CLOSING
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ArtisanBoxBlockEntity box) {
        if (!level.isClientSide) {
            box.updateLink(level, pos);
        }
        box.updateAnimation(level, pos, state);
    }

    // ---------------------------------------------------------------- linking

    /** True while a bound heart is held in range and the lid has room to open. */
    public boolean isLinked() {
        return this.ownerId != null;
    }

    /**
     * The player this box is linked to, or null when unlinked. Ownership follows whoever carries the
     * bound heart, so it changes hands without the box being re-bound.
     */
    @Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }

    @Nullable
    public UUID getHeartId() {
        return this.heartId;
    }

    public boolean isBound() {
        return this.heartId != null;
    }

    /** Slots the artisan heart update will give special behaviour to while linked. */
    public static boolean isLinkedSlot(int slot) {
        return slot >= 0 && slot < LINKED_SLOTS;
    }

    /**
     * Binds this box to a heart, replacing any previous binding. The old heart is not touched here —
     * it detects the broken link itself by comparing its stored id against {@link #getHeartId()}.
     */
    public void bindHeart(UUID newHeartId) {
        this.heartId = newHeartId;
        this.setChanged();
        this.syncToClients();
    }

    /**
     * Recomputes ownership each tick: a bound heart must be in some player's inventory within the
     * configured Manhattan distance, and the lid must have room to open.
     */
    private void updateLink(Level level, BlockPos pos) {
        Player holder = this.heartId == null ? null : findHeartHolder(level, pos);
        // Once the lid is up its own collision box fills the slab overhead, so only require clearance
        // while the box is shut — the same short-circuit vanilla's shulker box uses.
        boolean hasRoom = !this.isClosed() || canLidOpen(level, pos);
        UUID newOwner = holder != null && hasRoom ? holder.getUUID() : null;

        if (!Objects.equals(newOwner, this.ownerId)) {
            this.ownerId = newOwner;
            this.setChanged();
            this.syncToClients();
        }
        this.updateLidTarget(level);
    }

    /** The nearest player carrying this box's bound heart anywhere in their inventory. */
    @Nullable
    private Player findHeartHolder(Level level, BlockPos pos) {
        int maxDistance = LimenCommonConfig.ARTISAN_BOX_LINK_DISTANCE.get();
        Player closest = null;
        int closestDistance = Integer.MAX_VALUE;

        for (Player player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            int distance = player.blockPosition().distManhattan(pos);
            if (distance > maxDistance || distance >= closestDistance) {
                continue;
            }
            if (!carriesBoundHeart(player)) {
                continue;
            }
            closest = player;
            closestDistance = distance;
        }
        return closest;
    }

    /** Scans the main inventory, hotbar, offhand and armor slots for this box's bound heart. */
    private boolean carriesBoundHeart(Player player) {
        Inventory inventory = player.getInventory();
        return containsBoundHeart(inventory.items) || containsBoundHeart(inventory.offhand)
                || containsBoundHeart(inventory.armor);
    }

    private boolean containsBoundHeart(NonNullList<ItemStack> stacks) {
        if (this.heartId == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof ArtisanHeartItem
                    && this.heartId.equals(ArtisanHeartItem.getHeartId(stack))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- lid motion

    /**
     * Whether the lid has room to swing up: nothing solid within {@link #LID_CLEARANCE} blocks above.
     * {@code noCollision} covers blocks and, through {@code Entity.canBeCollidedWith}, shulkers and
     * boats — the only entities vanilla gives a blocking collision box to.
     */
    public static boolean canLidOpen(Level level, BlockPos pos) {
        AABB lidBounds = Shulker.getProgressDeltaAabb(Direction.UP, 0.0F, (float) LID_CLEARANCE)
                .move(pos)
                .deflate(1.0E-6D);
        return level.noCollision(lidBounds);
    }

    /**
     * Server-side: recomputes whether the lid should be up and syncs the flip, playing the same
     * sounds a shulker box uses. Viewers and the link both feed this one flag.
     */
    private void updateLidTarget(Level level) {
        if (level.isClientSide) {
            return;
        }

        boolean shouldBeOpen = this.openCount > 0 || this.isLinked();
        if (shouldBeOpen == this.lidOpen) {
            return;
        }

        this.lidOpen = shouldBeOpen;
        this.lidChangedAt = level.getGameTime();
        this.setChanged();
        this.syncToClients();
        playLidSound(level, this.getBlockPos(),
                shouldBeOpen ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE);
    }

    private static void playLidSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F,
                level.random.nextFloat() * 0.1F + 0.9F);
    }

    /**
     * Steers the animation toward {@link #lidOpen} and tracks lid progress for collision and entity
     * pushing. The visible lid motion is played by GeckoLib; this only mirrors how far along that
     * motion should be. Runs on both sides.
     */
    private void updateAnimation(Level level, BlockPos pos, BlockState state) {
        if (this.lidOpen && (this.animationStatus == AnimationStatus.CLOSED
                || this.animationStatus == AnimationStatus.CLOSING)) {
            this.animationStatus = AnimationStatus.OPENING;
        } else if (!this.lidOpen && (this.animationStatus == AnimationStatus.OPENED
                || this.animationStatus == AnimationStatus.OPENING)) {
            this.animationStatus = AnimationStatus.CLOSING;
        }

        this.progressOld = this.progress;

        switch (this.animationStatus) {
            case CLOSED -> this.progress = 0.0F;
            case OPENING -> {
                this.progress += 1.0F / OPEN_ANIMATION_TICKS;
                if (this.progress >= 1.0F) {
                    this.progress = 1.0F;
                    this.animationStatus = AnimationStatus.OPENED;
                    this.doNeighborUpdates(level, pos, state);
                }
                this.moveCollidedEntities(level, pos, state);
            }
            case OPENED -> this.progress = 1.0F;
            case CLOSING -> {
                this.progress -= 1.0F / OPEN_ANIMATION_TICKS;
                if (this.progress <= 0.0F) {
                    this.progress = 0.0F;
                    this.animationStatus = AnimationStatus.CLOSED;
                    this.doNeighborUpdates(level, pos, state);
                }
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER_LID, 0, this::lidAnimation));
    }

    private PlayState lidAnimation(AnimationState<ArtisanBoxBlockEntity> state) {
        return switch (this.animationStatus) {
            // Held on the open animation's last frame while the box stays open or linked, so no
            // separate idle animation is needed to keep the lid up.
            case OPENING, OPENED -> state.setAndContinue(OPEN);
            case CLOSING -> state.setAndContinue(CLOSE);
            // Closed needs no animation: the model's own rest pose is the closed lid.
            case CLOSED -> PlayState.STOP;
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    public AnimationStatus getAnimationStatus() {
        return this.animationStatus;
    }

    /** Interpolated lid progress, used for the growing collision box. */
    public float getProgress(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, this.progressOld, this.progress);
    }

    public AABB getBoundingBox() {
        return getProgressDeltaAabb(this.getProgress(1.0F));
    }

    /** The lid always opens upward, so the growing box only ever extends in +Y. */
    private static AABB getProgressDeltaAabb(float progress) {
        return Shapes.block().bounds().expandTowards(0.0D, progress * LID_CLEARANCE, 0.0D);
    }

    private void moveCollidedEntities(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof com.moonlightc.limen.block.ArtisanBoxBlock)) {
            return;
        }

        AABB box = getProgressDeltaAabb(this.progress).move(pos);
        for (Entity entity : level.getEntities(null, box)) {
            if (entity.getPistonPushReaction() == PushReaction.IGNORE) {
                continue;
            }
            entity.move(MoverType.SHULKER_BOX, new Vec3(0.0D, 0.05D, 0.0D));
        }
    }

    private void doNeighborUpdates(Level level, BlockPos pos, BlockState state) {
        state.updateNeighbourShapes(level, pos, 3);
    }

    // ------------------------------------------------------------- container

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public void startOpen(Player player) {
        if (player.isSpectator() || this.level == null) {
            return;
        }
        if (this.openCount < 0) {
            this.openCount = 0;
        }
        this.openCount++;
        if (this.openCount == 1) {
            this.level.gameEvent(player, GameEvent.CONTAINER_OPEN, this.getBlockPos());
        }
        this.updateLidTarget(this.level);
    }

    @Override
    public void stopOpen(Player player) {
        if (player.isSpectator() || this.level == null) {
            return;
        }
        this.openCount--;
        if (this.openCount <= 0) {
            this.openCount = 0;
            this.level.gameEvent(player, GameEvent.CONTAINER_CLOSE, this.getBlockPos());
        }
        // A linked box stays open with no viewer; updateLidTarget knows that and holds the lid up.
        this.updateLidTarget(this.level);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.limen.artisan_box");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ArtisanBoxMenu(containerId, inventory, this);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.loadFromTag(tag);
        this.heartId = tag.hasUUID(TAG_HEART_ID) ? tag.getUUID(TAG_HEART_ID) : null;
        this.ownerId = tag.hasUUID(TAG_OWNER_ID) ? tag.getUUID(TAG_OWNER_ID) : null;
        this.adoptLidState(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, false);
        }
        if (this.heartId != null) {
            tag.putUUID(TAG_HEART_ID, this.heartId);
        }
        if (this.ownerId != null) {
            tag.putUUID(TAG_OWNER_ID, this.ownerId);
        }
        // The lid flag is deliberately not saved. It is derived every tick from the viewer count and
        // the link, and viewers never survive a reload — so writing it can only preserve a stale open
        // lid, which then reloads already-open and skips its animation. Loading closed and letting the
        // first tick re-derive it means a still-in-range heart opens the box for real.
    }

    public void loadFromTag(CompoundTag tag) {
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag) && tag.contains("Items", 9)) {
            ContainerHelper.loadAllItems(tag, this.items);
        }
    }

    public boolean isClosed() {
        return this.animationStatus == AnimationStatus.CLOSED;
    }

    @Override
    public double getTick(Object blockEntity) {
        return software.bernie.geckolib.util.RenderUtils.getCurrentTick();
    }

    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Only the lid and link state need to reach clients. Contents are deliberately left out — like a
     * vanilla shulker box, they are sent when a player actually opens the container.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_LID_OPEN, this.lidOpen);
        tag.putLong(TAG_LID_AGE, this.lidAge());
        if (this.heartId != null) {
            tag.putUUID(TAG_HEART_ID, this.heartId);
        }
        if (this.ownerId != null) {
            tag.putUUID(TAG_OWNER_ID, this.ownerId);
        }
        return tag;
    }

    /**
     * Ticks since the lid last flipped, as seen by the server at the moment the packet is built.
     * Sent instead of the raw timestamp because the client's game time is its own and would not line
     * up. {@link Long#MAX_VALUE} means "as long as this box has existed", i.e. straight off disk.
     */
    private long lidAge() {
        if (this.lidChangedAt == Long.MIN_VALUE || this.level == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, this.level.getGameTime() - this.lidChangedAt);
    }

    /** Client-side counterpart of {@link #getUpdateTag()}; leaves the container contents alone. */
    @Override
    public void handleUpdateTag(CompoundTag tag) {
        this.heartId = tag.hasUUID(TAG_HEART_ID) ? tag.getUUID(TAG_HEART_ID) : null;
        this.ownerId = tag.hasUUID(TAG_OWNER_ID) ? tag.getUUID(TAG_OWNER_ID) : null;
        this.adoptLidState(tag);
    }

    /**
     * Applies a synced lid flag, deciding whether it should animate or be snapped into place.
     *
     * <p>Every later sync animates normally. Only the very first one this instance sees needs a
     * decision, and a per-instance flag alone cannot make it: walking up to a box that has stood open
     * for an hour and walking into one whose lid opened as the chunk was still in flight both look
     * like "first packet, already open". So the server sends how long ago the flip happened, and an
     * open that is younger than {@link #LID_ANIMATE_ON_SIGHT_TICKS} is treated as new to this client
     * and animates — which is what fixes the skipped opening when a player runs up carrying the heart.
     *
     * <p>Only opening is animated on sight. A box that reads closed is left closed: the closed lid is
     * the model's own rest pose, so snapping it up just to play a close would be a worse artifact than
     * the missing animation.
     */
    private void adoptLidState(CompoundTag tag) {
        this.lidOpen = tag.getBoolean(TAG_LID_OPEN);
        if (this.adoptedLidState) {
            return;
        }
        this.adoptedLidState = true;

        long age = tag.contains(TAG_LID_AGE) ? tag.getLong(TAG_LID_AGE) : Long.MAX_VALUE;
        boolean animateOpen = this.lidOpen && age < LID_ANIMATE_ON_SIGHT_TICKS;
        // Starting from closed lets updateAnimation drive the lid up the rest of the way itself.
        this.animationStatus = this.lidOpen && !animateOpen ? AnimationStatus.OPENED : AnimationStatus.CLOSED;
        this.progress = this.animationStatus == AnimationStatus.OPENED ? 1.0F : 0.0F;
        this.progressOld = this.progress;
    }
}
