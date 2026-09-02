package com.moonlightc.limen.network;

import com.moonlightc.limen.event.ClientChainswordEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server tells the client how hot the chainsword currently is (0..1) so the client can draw the
 * accumulating "item cooldown" overlay on the icon as heat builds toward overheat.
 */
public record ChainswordHeatPacket(float progress) {
    public static void encode(ChainswordHeatPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.progress());
    }

    public static ChainswordHeatPacket decode(FriendlyByteBuf buffer) {
        return new ChainswordHeatPacket(buffer.readFloat());
    }

    public static void handle(ChainswordHeatPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientChainswordEvents.setHeatProgress(packet.progress());
        }
        ctx.setPacketHandled(true);
    }
}
