package com.moonlightc.limen.network;

import com.moonlightc.limen.hotbar.ArtisanHeartLogic;
import com.moonlightc.limen.hotbar.ArtisanHotbarSync;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import com.moonlightc.limen.hotbar.ArtisanHotbarsCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks to select an artisan hotbar slot, or {@link ArtisanHotbars#NONE} to go back to a plain
 * vanilla slot. The server re-validates that the slot is unlocked before moving anything, then syncs
 * the result back.
 */
public record ArtisanHotbarSelectPacket(int slot) {
    public static void encode(ArtisanHotbarSelectPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot());
    }

    public static ArtisanHotbarSelectPacket decode(FriendlyByteBuf buffer) {
        return new ArtisanHotbarSelectPacket(buffer.readVarInt());
    }

    public static void handle(ArtisanHotbarSelectPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            int slot = ArtisanHeartLogic.sanitizeSlot(sender, packet.slot());
            ArtisanHotbarsCapability.get(sender).ifPresent(hotbars -> {
                if (hotbars.select(sender, slot)) {
                    ArtisanHotbarSync.sync(sender, hotbars);
                }
            });
        });
        ctx.setPacketHandled(true);
    }
}
