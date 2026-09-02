package com.moonlightc.limen.inventory;

import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import com.moonlightc.limen.registry.ModMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Artisan box container. Slot geometry matches a vanilla shulker box so the screen can reuse the
 * familiar 3x9 layout, but the box gets its own menu type and screen for its custom background.
 *
 * <p>{@link ArtisanBoxBlockEntity#LINKED_SLOTS} — the first row — is where the artisan heart update
 * will add linked-state behaviour. {@link #isLinkedSlot(int)} maps a menu slot index onto that range
 * so the behaviour can be added without moving any slots.
 */
public class ArtisanBoxMenu extends AbstractContainerMenu {
    private final Container container;

    /** Client-side constructor: the real contents arrive from the server. */
    public ArtisanBoxMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(ArtisanBoxBlockEntity.CONTAINER_SIZE));
    }

    public ArtisanBoxMenu(int containerId, Inventory inventory, Container container) {
        super(ModMenuTypes.ARTISAN_BOX.get(), containerId);
        checkContainerSize(container, ArtisanBoxBlockEntity.CONTAINER_SIZE);
        this.container = container;
        container.startOpen(inventory.player);

        // Box contents, 3 rows of 9, at the vanilla shulker box offsets.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new ShulkerBoxSlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        // Player inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    /** True when this menu slot index is one of the box's reserved linked slots. */
    public static boolean isLinkedSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < ArtisanBoxBlockEntity.CONTAINER_SIZE
                && ArtisanBoxBlockEntity.isLinkedSlot(slotIndex);
    }

    public Container getContainer() {
        return this.container;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        ItemStack original = slotStack.copy();
        int boxSize = this.container.getContainerSize();

        if (index < boxSize) {
            if (!this.moveItemStackTo(slotStack, boxSize, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(slotStack, 0, boxSize, false)) {
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
        this.container.stopOpen(player);
    }
}
