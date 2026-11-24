package com.moonlightc.limen.network;

import com.moonlightc.limen.entity.MechanicTrappedState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record MechanicTrappedPacket(int entityId, UUID entityUuid, boolean trapped, float yaw) {
    public static void encode(MechanicTrappedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeUUID(packet.entityUuid());
        buffer.writeBoolean(packet.trapped());
        buffer.writeFloat(packet.yaw());
    }

    public static MechanicTrappedPacket decode(FriendlyByteBuf buffer) {
        return new MechanicTrappedPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean(),
                buffer.readFloat());
    }

    public static void handle(MechanicTrappedPacket packet, Supplier<NetworkEvent.Context> context) {
        // Registered with consumerMainThread: already on the correct client game thread.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(packet));
        context.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(MechanicTrappedPacket packet) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            Entity entity = minecraft.level.getEntity(packet.entityId());
            if (entity instanceof Villager villager && entity.getUUID().equals(packet.entityUuid())) {
                MechanicTrappedState.applySyncedState(villager, packet.trapped(), packet.yaw());
            }
        }
    }
}
