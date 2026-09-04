package com.moonlightc.limen.hotbar;

import com.moonlightc.limen.network.ArtisanHotbarsSyncPacket;
import com.moonlightc.limen.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Pushes artisan hotbar state to its owning client. */
public final class ArtisanHotbarSync {
    private ArtisanHotbarSync() {
    }

    public static void sync(ServerPlayer player, ArtisanHotbars hotbars) {
        // The mounted row lives in the real hotbar, so the vanilla inventory has to be resent too or
        // the client keeps showing the row it had before the switch.
        //
        // Order matters: vanilla's slot packets give any hotbar stack that grew the pickup bounce, and
        // the state packet below is what cancels it. Sending the state packet last is what guarantees
        // the client handles it after those slot packets.
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                ArtisanHotbarsSyncPacket.of(hotbars));
        hotbars.clearDirty();
    }

    public static void sync(ServerPlayer player) {
        ArtisanHotbarsCapability.get(player).ifPresent(hotbars -> sync(player, hotbars));
    }
}
