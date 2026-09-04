package com.moonlightc.limen.network;

import com.moonlightc.limen.hotbar.ArtisanHotbars;
import com.moonlightc.limen.hotbar.ArtisanHotbarsCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server pushes the whole artisan hotbar state to its owner: the three stored stacks plus the mount
 * bookkeeping. It is small and only changes on an explicit action, so sending it whole is simpler and
 * cheaper than tracking per-slot deltas.
 */
public record ArtisanHotbarsSyncPacket(int selected, int stashIndex, ItemStack stashedStack,
                                       NonNullList<ItemStack> items) {
    public static void encode(ArtisanHotbarsSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.selected());
        buffer.writeVarInt(packet.stashIndex());
        buffer.writeItem(packet.stashedStack());
        for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
            buffer.writeItem(packet.items().get(slot));
        }
    }

    public static ArtisanHotbarsSyncPacket decode(FriendlyByteBuf buffer) {
        int selected = buffer.readVarInt();
        int stashIndex = buffer.readVarInt();
        ItemStack stashedStack = buffer.readItem();
        NonNullList<ItemStack> items = NonNullList.withSize(ArtisanHotbars.SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
            items.set(slot, buffer.readItem());
        }
        return new ArtisanHotbarsSyncPacket(selected, stashIndex, stashedStack, items);
    }

    public static ArtisanHotbarsSyncPacket of(ArtisanHotbars hotbars) {
        NonNullList<ItemStack> items = NonNullList.withSize(ArtisanHotbars.SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
            items.set(slot, hotbars.getStored(slot));
        }
        return new ArtisanHotbarsSyncPacket(hotbars.getSelected(), hotbars.getStashIndex(),
                hotbars.getStashedStack(), items);
    }

    public static void handle(ArtisanHotbarsSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            ArtisanHotbarsCapability.get(player).ifPresent(hotbars -> {
                int previousStash = hotbars.getStashIndex();
                hotbars.applySync(packet.selected(), packet.stashIndex(), packet.stashedStack());
                for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
                    hotbars.setStored(slot, packet.items().get(slot));
                }
                // Mounting swaps a hotbar slot's contents, and vanilla's ClientPacketListener gives any
                // hotbar stack that arrives larger than before the pickup bounce. That reads as the row
                // "jumping" every time the player switches in or out, so the bounce is cleared on both
                // the slot being left and the one being taken. The server sends its slot updates ahead
                // of this packet, so by now the pop times are already set and can be undone.
                clearPop(player, previousStash);
                clearPop(player, packet.stashIndex());
            });
        });
        ctx.setPacketHandled(true);
    }

    private static void clearPop(net.minecraft.world.entity.player.Player player, int index) {
        if (index < 0 || index >= player.getInventory().items.size()) {
            return;
        }
        player.getInventory().items.get(index).setPopTime(0);
    }
}
