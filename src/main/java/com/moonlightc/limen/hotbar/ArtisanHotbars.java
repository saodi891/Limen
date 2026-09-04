package com.moonlightc.limen.hotbar;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * The three extra hotbar slots an artisan heart grants, shown to the right of the vanilla hotbar.
 *
 * <p>Selection works by <em>mounting</em>: when an artisan slot is selected, its stack is moved into
 * the player's real hotbar at {@code inventory.selected}, and the vanilla stack that was there is
 * stashed here. That is what makes everything else work without touching vanilla code — item use,
 * attacking, held-item rendering and the F-key offhand swap all read
 * {@code inventory.items[inventory.selected]}, so they operate on the mounted stack for free.
 *
 * <p>Consequences of mounting, both of which the rest of the code has to respect:
 * <ul>
 *   <li>the mounted artisan slot's entry in {@link #items} is a stale placeholder — the live stack is
 *       in the inventory;</li>
 *   <li>the vanilla slot at {@link #stashIndex} does not hold its own item while mounted — the real
 *       one is in {@link #stashedStack}.</li>
 * </ul>
 * Always go through {@link #getArtisanStack} and {@link #getVanillaStack} rather than reading either
 * container directly.
 *
 * <p>Kept as a capability rather than by growing {@link Inventory}, because the vanilla inventory's
 * slot count is baked into every container packet: growing it would desync vanilla clients and break
 * vanilla's own slot arithmetic.
 *
 * <p>The slots persist even when no heart is carried. Losing the heart hides them and blocks
 * selection, but nothing is dropped — picking a heart back up restores whatever was stored.
 */
public class ArtisanHotbars implements INBTSerializable<CompoundTag> {
    public static final int SLOT_COUNT = 3;
    /** Selection positions across the whole hotbar: 0-8 vanilla, 9-11 artisan. */
    public static final int VANILLA_SLOT_COUNT = 9;
    public static final int TOTAL_POSITIONS = VANILLA_SLOT_COUNT + SLOT_COUNT;
    /** No artisan slot selected; the player is on a plain vanilla slot. */
    public static final int NONE = -1;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SELECTED = "Selected";
    private static final String TAG_STASH_INDEX = "StashIndex";
    private static final String TAG_STASHED = "Stashed";
    private static final String TAG_SLOT = "Slot";

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int selected = NONE;
    /** Vanilla hotbar index the selected artisan stack is mounted into, or {@link #NONE}. */
    private int stashIndex = NONE;
    /** The vanilla stack displaced by the mount. */
    private ItemStack stashedStack = ItemStack.EMPTY;
    private boolean dirty = true;

    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    /** True for a whole-hotbar position that refers to one of the artisan slots. */
    public static boolean isArtisanPosition(int position) {
        return position >= VANILLA_SLOT_COUNT && position < TOTAL_POSITIONS;
    }

    public int getSelected() {
        return this.selected;
    }

    public boolean hasArtisanSelected() {
        return isValidSlot(this.selected);
    }

    public int getStashIndex() {
        return this.stashIndex;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public void setChanged() {
        this.dirty = true;
    }

    // ------------------------------------------------------------- selection

    /**
     * Selects an artisan slot, or {@link #NONE} to return to the plain vanilla hotbar. Returns true if
     * anything changed.
     */
    public boolean select(Player player, int slot) {
        int target = isValidSlot(slot) ? slot : NONE;
        if (target == this.selected) {
            return false;
        }

        unmount(player);
        if (target != NONE) {
            mount(player, target);
        }
        this.selected = target;
        this.setChanged();
        return true;
    }

    /** Mounts an artisan stack into whichever vanilla slot the player currently has selected. */
    private void mount(Player player, int slot) {
        Inventory inventory = player.getInventory();
        this.stashIndex = inventory.selected;
        this.stashedStack = inventory.items.get(this.stashIndex);
        inventory.items.set(this.stashIndex, this.items.get(slot));
        this.items.set(slot, ItemStack.EMPTY);
        inventory.setChanged();
    }

    /** Returns the mounted stack to storage and the stashed vanilla stack to the inventory. */
    private void unmount(Player player) {
        if (this.stashIndex == NONE) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (isValidSlot(this.selected)) {
            this.items.set(this.selected, inventory.items.get(this.stashIndex));
        }
        inventory.items.set(this.stashIndex, this.stashedStack);
        inventory.setChanged();

        this.stashIndex = NONE;
        this.stashedStack = ItemStack.EMPTY;
    }

    /** Returns to the vanilla hotbar, e.g. when the heart is lost or before a bulk swap. */
    public boolean deselect(Player player) {
        return select(player, NONE);
    }

    /**
     * Reconciles a mount that vanilla moved out from under us: pressing a number key changes
     * {@code inventory.selected} without going through {@link #select}, which would strand the
     * artisan stack in a slot the player is no longer holding.
     */
    public boolean reconcile(Player player) {
        if (this.stashIndex == NONE || player.getInventory().selected == this.stashIndex) {
            return false;
        }
        return deselect(player);
    }

    // -------------------------------------------------------------- access

    /** The stack shown in an artisan slot, resolving the mounted slot to the real hotbar. */
    public ItemStack getArtisanStack(Player player, int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        if (slot == this.selected && this.stashIndex != NONE) {
            return player.getInventory().items.get(this.stashIndex);
        }
        return this.items.get(slot);
    }

    /** Writes to an artisan slot, resolving the mounted slot to the real hotbar. */
    public void setArtisanStack(Player player, int slot, ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }
        if (slot == this.selected && this.stashIndex != NONE) {
            player.getInventory().items.set(this.stashIndex, stack);
            player.getInventory().setChanged();
        } else {
            this.items.set(slot, stack);
        }
        this.setChanged();
    }

    /**
     * The stack a vanilla hotbar slot really holds. Differs from {@code inventory.items} only at
     * {@link #stashIndex}, where an artisan stack is currently mounted.
     */
    public ItemStack getVanillaStack(Player player, int index) {
        if (index == this.stashIndex) {
            return this.stashedStack;
        }
        return player.getInventory().items.get(index);
    }

    /** Raw storage access, for syncing and persistence only. */
    public ItemStack getStored(int slot) {
        return isValidSlot(slot) ? this.items.get(slot) : ItemStack.EMPTY;
    }

    /** Raw storage write, for syncing only. */
    public void setStored(int slot, ItemStack stack) {
        if (isValidSlot(slot)) {
            this.items.set(slot, stack);
        }
    }

    public ItemStack getStashedStack() {
        return this.stashedStack;
    }

    /**
     * Snapshot of all three slots as they would look unmounted, for writing onto the heart. The
     * mounted slot is resolved back out of the vanilla hotbar, so this is what the player would get
     * if they deselected right now.
     */
    public NonNullList<ItemStack> snapshot(Player player) {
        NonNullList<ItemStack> copy = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            copy.set(slot, getArtisanStack(player, slot).copy());
        }
        return copy;
    }

    /** Applies a synced snapshot client-side, without moving anything itself. */
    public void applySync(int selected, int stashIndex, ItemStack stashedStack) {
        this.selected = isValidSlot(selected) ? selected : NONE;
        this.stashIndex = stashIndex;
        this.stashedStack = stashedStack;
    }

    /**
     * Drops the artisan slots' contents at the player's feet. Whatever is mounted is left alone — it
     * is in the vanilla hotbar, which vanilla drops itself.
     */
    public void dropAll(Player player) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = this.items.get(slot);
            if (!stack.isEmpty()) {
                player.drop(stack, true, false);
                this.items.set(slot, ItemStack.EMPTY);
            }
        }
        this.setChanged();
    }

    // -------------------------------------------------------------- storage

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag slotTag = new CompoundTag();
            slotTag.putByte(TAG_SLOT, (byte) slot);
            stack.save(slotTag);
            list.add(slotTag);
        }
        tag.put(TAG_ITEMS, list);
        tag.putInt(TAG_SELECTED, this.selected);
        tag.putInt(TAG_STASH_INDEX, this.stashIndex);
        if (!this.stashedStack.isEmpty()) {
            tag.put(TAG_STASHED, this.stashedStack.save(new CompoundTag()));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.items.clear();
        ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag slotTag = list.getCompound(i);
            int slot = slotTag.getByte(TAG_SLOT) & 0xFF;
            if (isValidSlot(slot)) {
                this.items.set(slot, ItemStack.of(slotTag));
            }
        }
        this.selected = isValidSlot(tag.getInt(TAG_SELECTED)) ? tag.getInt(TAG_SELECTED) : NONE;
        this.stashIndex = tag.contains(TAG_STASH_INDEX) ? tag.getInt(TAG_STASH_INDEX) : NONE;
        this.stashedStack = tag.contains(TAG_STASHED)
                ? ItemStack.of(tag.getCompound(TAG_STASHED))
                : ItemStack.EMPTY;
        this.setChanged();
    }

    /** Copies another player's slots wholesale, for respawn and dimension changes. */
    public void copyFrom(ArtisanHotbars other) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            this.items.set(slot, other.items.get(slot).copy());
        }
        this.selected = other.selected;
        this.stashIndex = other.stashIndex;
        this.stashedStack = other.stashedStack.copy();
        this.setChanged();
    }
}
