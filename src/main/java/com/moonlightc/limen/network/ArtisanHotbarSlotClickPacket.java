package com.moonlightc.limen.network;

import com.moonlightc.limen.hotbar.ArtisanHeartLogic;
import com.moonlightc.limen.hotbar.ArtisanHotbarSync;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import com.moonlightc.limen.hotbar.ArtisanHotbarsCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client edits one artisan hotbar slot from the inventory screen. Vanilla's slot-click machinery only
 * knows about menu slots, and the artisan slots are not part of any menu, so the exchange with the
 * cursor stack is done here instead.
 */
public record ArtisanHotbarSlotClickPacket(int slot, boolean splitStack) {
    public static void encode(ArtisanHotbarSlotClickPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot());
        buffer.writeBoolean(packet.splitStack());
    }

    public static ArtisanHotbarSlotClickPacket decode(FriendlyByteBuf buffer) {
        return new ArtisanHotbarSlotClickPacket(buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(ArtisanHotbarSlotClickPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !ArtisanHotbars.isValidSlot(packet.slot())) {
                return;
            }
            // Configuring the slots requires a heart, same as selecting them.
            if (!ArtisanHeartLogic.hasHeart(sender)) {
                return;
            }

            ArtisanHotbarsCapability.get(sender).ifPresent(hotbars -> {
                applyClick(sender, hotbars, packet);
                ArtisanHotbarSync.sync(sender, hotbars);
            });
        });
        ctx.setPacketHandled(true);
    }

    private static void applyClick(ServerPlayer player, ArtisanHotbars hotbars,
                                   ArtisanHotbarSlotClickPacket packet) {
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack slotStack = hotbars.getArtisanStack(player, packet.slot());

        if (carried.isEmpty()) {
            // Taking: right click takes half, left click takes everything.
            if (slotStack.isEmpty()) {
                return;
            }
            int taken = packet.splitStack() ? (slotStack.getCount() + 1) / 2 : slotStack.getCount();
            ItemStack removed = slotStack.split(taken);
            player.containerMenu.setCarried(removed);
            hotbars.setArtisanStack(player, packet.slot(),
                    slotStack.isEmpty() ? ItemStack.EMPTY : slotStack);
            return;
        }

        if (slotStack.isEmpty()) {
            // Placing into an empty slot: right click places one.
            int placed = packet.splitStack() ? 1 : carried.getCount();
            hotbars.setArtisanStack(player, packet.slot(), carried.split(placed));
            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return;
        }

        if (ItemStack.isSameItemSameTags(carried, slotStack)) {
            // Merging: right click adds one, left click tops the stack up.
            int room = slotStack.getMaxStackSize() - slotStack.getCount();
            if (room <= 0) {
                return;
            }
            int moved = Math.min(packet.splitStack() ? 1 : carried.getCount(), room);
            slotStack.grow(moved);
            carried.shrink(moved);
            hotbars.setArtisanStack(player, packet.slot(), slotStack);
            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return;
        }

        // Different items: exchange them.
        hotbars.setArtisanStack(player, packet.slot(), carried);
        player.containerMenu.setCarried(slotStack);
    }
}
