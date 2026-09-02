package com.moonlightc.limen.network;

import com.moonlightc.limen.event.ChainswordEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client tells the server whether the attack key is currently held while a chainsword is in the
 * main hand. The server owns the actual damage logic; this packet only reports input state, which
 * the client is the authority on.
 */
public record ChainswordHoldPacket(boolean holding) {
    public static void encode(ChainswordHoldPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.holding());
    }

    public static ChainswordHoldPacket decode(FriendlyByteBuf buffer) {
        return new ChainswordHoldPacket(buffer.readBoolean());
    }

    public static void handle(ChainswordHoldPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            ChainswordEvents.setHolding(sender, packet.holding());
        }
        ctx.setPacketHandled(true);
    }
}
