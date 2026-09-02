package com.moonlightc.limen.network;

import com.moonlightc.limen.event.ClientChainswordEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server tells nearby clients that a player has started or stopped grinding with the chainsword.
 * Needed because the sawing arm pose has to render on other players too, not just the holder.
 */
public record ChainswordGrindPacket(int playerId, boolean grinding) {
    public static void encode(ChainswordGrindPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.playerId());
        buffer.writeBoolean(packet.grinding());
    }

    public static ChainswordGrindPacket decode(FriendlyByteBuf buffer) {
        return new ChainswordGrindPacket(buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(ChainswordGrindPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientChainswordEvents.setGrinding(packet.playerId(), packet.grinding());
        }
        ctx.setPacketHandled(true);
    }
}
