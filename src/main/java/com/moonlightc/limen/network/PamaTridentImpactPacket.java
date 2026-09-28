package com.moonlightc.limen.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server tells nearby clients that a PAMA trident settled at a point, so each client plays the one-shot
 * expanding impact wave. Carries only the impact centre; radius, colour and duration are shared constants.
 */
public record PamaTridentImpactPacket(double x, double y, double z) {
    public static void encode(PamaTridentImpactPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x());
        buffer.writeDouble(packet.y());
        buffer.writeDouble(packet.z());
    }

    public static PamaTridentImpactPacket decode(FriendlyByteBuf buffer) {
        return new PamaTridentImpactPacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(PamaTridentImpactPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.moonlightc.limen.client.PamaTridentImpactWaves.add(packet.x(), packet.y(), packet.z());
            }
        });
        ctx.setPacketHandled(true);
    }
}
