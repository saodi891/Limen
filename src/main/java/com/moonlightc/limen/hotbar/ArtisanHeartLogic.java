package com.moonlightc.limen.hotbar;

import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import com.moonlightc.limen.item.ArtisanHeartItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared rules for when the artisan hotbar slots are usable and what the offhand heart does. Called
 * from both sides so the client can predict availability without guessing.
 */
public final class ArtisanHeartLogic {
    private ArtisanHeartLogic() {
    }

    /**
     * Any artisan heart anywhere in the player's inventory unlocks the extra slots — including inside
     * the artisan slots themselves.
     *
     * <p>Counting the artisan slots matters: without it, stashing the heart in one of them would make
     * the slots lock and deselect, stranding the heart where the player can no longer reach it.
     */
    public static boolean hasHeart(Player player) {
        Inventory inventory = player.getInventory();
        if (containsHeart(inventory.items) || containsHeart(inventory.offhand)
                || containsHeart(inventory.armor)) {
            return true;
        }

        ArtisanHotbars hotbars = getHotbars(player);
        if (hotbars == null) {
            return false;
        }
        if (hotbars.getStashedStack().getItem() instanceof ArtisanHeartItem) {
            return true;
        }
        for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
            if (hotbars.getStored(slot).getItem() instanceof ArtisanHeartItem) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsHeart(Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof ArtisanHeartItem) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies the current artisan slots onto every heart the player carries, so their contents can be
     * read off the item once it leaves the inventory and the slots lock.
     *
     * <p>Every heart gets the same snapshot rather than just one: the slots belong to the player, not
     * to a particular heart, so whichever heart the player takes out has to show them. Only the
     * player's own inventory is written — a heart already stored inside an artisan slot is skipped,
     * since writing the slots into an item held by those slots would nest a copy on every update.
     */
    public static void writeSlotsToHearts(Player player) {
        ArtisanHotbars hotbars = getHotbars(player);
        if (hotbars == null) {
            return;
        }

        NonNullList<ItemStack> snapshot = hotbars.snapshot(player);
        int mounted = hotbars.getStashIndex();
        Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.items.size(); index++) {
            // The mounted slot holds an artisan stack, not the player's own item; skip it so a heart
            // sitting in an artisan slot is not given a copy of the slots it lives in.
            if (index != mounted) {
                writeIfHeart(inventory.items.get(index), snapshot);
            }
        }
        for (ItemStack stack : inventory.offhand) {
            writeIfHeart(stack, snapshot);
        }
        for (ItemStack stack : inventory.armor) {
            writeIfHeart(stack, snapshot);
        }
    }

    private static void writeIfHeart(ItemStack stack, NonNullList<ItemStack> snapshot) {
        if (stack.getItem() instanceof ArtisanHeartItem) {
            ArtisanHeartItem.setStoredSlots(stack, snapshot);
        }
    }

    /**
     * The artisan box this player's offhand heart is linked to, if the full chain holds: the offhand
     * holds a heart, that heart is bound, its box is loaded in this dimension, the box still
     * recognises this heart, and the box's link is currently active with this player as owner.
     */
    public static Optional<ArtisanBoxBlockEntity> getLinkedBoxForOffhandHeart(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (!(offhand.getItem() instanceof ArtisanHeartItem)) {
            return Optional.empty();
        }

        UUID heartId = ArtisanHeartItem.getHeartId(offhand);
        if (heartId == null) {
            return Optional.empty();
        }

        Optional<GlobalPos> bound = ArtisanHeartItem.getBoundBox(offhand);
        if (bound.isEmpty()) {
            return Optional.empty();
        }

        GlobalPos target = bound.get();
        Level level = player.level();
        if (!target.dimension().equals(level.dimension())) {
            return Optional.empty();
        }

        BlockPos pos = target.pos();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ArtisanBoxBlockEntity box)) {
            return Optional.empty();
        }
        if (!heartId.equals(box.getHeartId()) || !box.isLinked()
                || !player.getUUID().equals(box.getOwnerId())) {
            return Optional.empty();
        }
        return Optional.of(box);
    }

    /** True when pressing the swap-offhand key should exchange the hotbar with a box instead. */
    public static boolean canSwapWithBox(Player player) {
        return getLinkedBoxForOffhandHeart(player).isPresent();
    }

    /**
     * Exchanges the player's nine vanilla hotbar slots with the linked box's first nine slots, one for
     * one. Returns false when the link no longer holds, in which case the caller should let the
     * vanilla hand swap run instead.
     *
     * <p>Any mounted artisan slot is unmounted first, so the swap always sees the player's real
     * vanilla hotbar rather than a hotbar with an artisan stack sitting in it.
     */
    public static boolean swapHotbarWithBox(Player player) {
        ArtisanBoxBlockEntity box = getLinkedBoxForOffhandHeart(player).orElse(null);
        if (box == null) {
            return false;
        }

        ArtisanHotbars hotbars = getHotbars(player);
        if (hotbars != null) {
            hotbars.deselect(player);
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < ArtisanHotbars.VANILLA_SLOT_COUNT; slot++) {
            ItemStack fromHotbar = inventory.items.get(slot);
            ItemStack fromBox = box.getItem(slot);
            // The heart itself lives in the offhand, so it can never be caught by this loop; no
            // special-casing needed to stop a player swapping away their own link.
            inventory.items.set(slot, fromBox);
            box.setItem(slot, fromHotbar);
        }
        inventory.setChanged();
        box.setChanged();
        return true;
    }

    /** Artisan slot the player may select, or {@link ArtisanHotbars#NONE} when it is not unlocked. */
    public static int sanitizeSlot(Player player, int requestedSlot) {
        if (!ArtisanHotbars.isValidSlot(requestedSlot) || !hasHeart(player)) {
            return ArtisanHotbars.NONE;
        }
        return requestedSlot;
    }

    @Nullable
    public static ArtisanHotbars getHotbars(Player player) {
        return ArtisanHotbarsCapability.get(player).orElse(null);
    }

    /**
     * The player's position along the combined hotbar: 0-8 for a vanilla slot, 9-11 for an artisan
     * slot. Used to make scrolling walk across both in one continuous line.
     */
    public static int currentPosition(Player player) {
        ArtisanHotbars hotbars = getHotbars(player);
        if (hotbars != null && hotbars.hasArtisanSelected()) {
            return ArtisanHotbars.VANILLA_SLOT_COUNT + hotbars.getSelected();
        }
        return player.getInventory().selected;
    }

    /**
     * Next position when scrolling, wrapping across the vanilla slots and however many artisan slots
     * are currently unlocked. Without a heart this collapses to vanilla's own nine-slot wrap.
     */
    public static int cyclePosition(Player player, int currentPosition, int direction) {
        int positions = hasHeart(player) ? ArtisanHotbars.TOTAL_POSITIONS : ArtisanHotbars.VANILLA_SLOT_COUNT;
        int next = (currentPosition + direction) % positions;
        return next < 0 ? next + positions : next;
    }
}
