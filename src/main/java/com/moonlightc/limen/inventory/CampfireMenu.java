package com.moonlightc.limen.inventory;

import com.moonlightc.limen.registry.ModItems;
import com.moonlightc.limen.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import org.jetbrains.annotations.Nullable;

/**
 * Roasting station opened by holding a marshmallow over a lit campfire.
 *
 * <p>The station deliberately does not reuse the campfire's own four food slots. Vanilla
 * {@code cookTick} ejects any stack whose {@code cookingTime} is zero onto the ground on the very
 * next tick, and both cooking arrays are private with no access-transformer entry, so a stack
 * written straight into {@code CampfireBlockEntity.getItems()} would be spat out instantly. The
 * station therefore owns its own contents and its own progress.
 */
public class CampfireMenu extends AbstractContainerMenu {
    public static final int ROAST_SLOTS = 4;
    /** Ten seconds per marshmallow. Progress only runs while the screen is open. */
    public static final int ROAST_TICKS = 200;

    public static final int SLOT_ROW_X = 53;
    public static final int SLOT_ROW_Y = 20;
    public static final int SLOT_PITCH = 18;

    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = 71;
    private static final int HOTBAR_Y = 129;

    private final Container roasting;
    private final ContainerData progress;
    private final ContainerLevelAccess access;

    /** Client-side constructor. The campfire position rides along in the open packet's buffer. */
    public CampfireMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, new SimpleContainer(ROAST_SLOTS), new SimpleContainerData(ROAST_SLOTS),
                ContainerLevelAccess.create(inventory.player.level(), data.readBlockPos()));
    }

    public CampfireMenu(int containerId, Inventory inventory, Container roasting, ContainerData progress,
                        ContainerLevelAccess access) {
        super(ModMenuTypes.CAMPFIRE.get(), containerId);
        checkContainerSize(roasting, ROAST_SLOTS);
        checkContainerDataCount(progress, ROAST_SLOTS);
        this.roasting = roasting;
        this.progress = progress;
        this.access = access;

        for (int slot = 0; slot < ROAST_SLOTS; slot++) {
            this.addSlot(new RoastingSlot(roasting, slot, SLOT_ROW_X + slot * SLOT_PITCH, SLOT_ROW_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9,
                        INVENTORY_X + col * SLOT_PITCH, INVENTORY_Y + row * SLOT_PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, INVENTORY_X + col * SLOT_PITCH, HOTBAR_Y));
        }

        this.addDataSlots(progress);
    }

    /** The toasted counterpart of a raw marshmallow, or null when the stack cannot be roasted. */
    @Nullable
    public static Item roastResult(ItemStack stack) {
        if (stack.is(ModItems.MARSHMALLOW_ONASTICK.get())) {
            return ModItems.COOKED_MARSHMALLOW_ONASTICK.get();
        }
        if (stack.is(ModItems.MARSHMALLOW_ONABLAZEROD.get())) {
            return ModItems.COOKED_MARSHMALLOW_ONABLAZEROD.get();
        }
        return null;
    }

    /** Roasting ticks elapsed on one slot, readable client-side once the data slot has synced. */
    public int getRoastTicks(int slot) {
        return this.progress.get(slot);
    }

    /** Advances every roasting slot by one tick. Driven from the owning player's server tick. */
    public void roastTick() {
        for (int slot = 0; slot < ROAST_SLOTS; slot++) {
            Item result = roastResult(this.roasting.getItem(slot));
            if (result == null) {
                this.progress.set(slot, 0);
                continue;
            }
            int ticks = this.progress.get(slot) + 1;
            if (ticks < ROAST_TICKS) {
                this.progress.set(slot, ticks);
                continue;
            }
            this.progress.set(slot, 0);
            this.roasting.setItem(slot, new ItemStack(result));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) -> CampfireBlock.isLitCampfire(level.getBlockState(pos))
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D, false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        ItemStack original = slotStack.copy();

        if (index < ROAST_SLOTS) {
            if (!this.moveItemStackTo(slotStack, ROAST_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(slotStack, 0, ROAST_SLOTS, false)) {
            return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.roasting);
    }

    /** Accepts only raw marshmallows, so the station cannot double as incidental storage. */
    private static class RoastingSlot extends Slot {
        RoastingSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return roastResult(stack) != null;
        }
    }
}
