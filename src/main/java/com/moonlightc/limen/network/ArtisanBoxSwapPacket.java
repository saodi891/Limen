package com.moonlightc.limen.network;

import com.moonlightc.limen.hotbar.ArtisanHeartLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client reports that the swap-offhand key was pressed while an offhand artisan heart is linked. The
 * server re-checks the link and, if it holds, swaps the live hotbar with the box's first nine slots.
 *
 * <p>The vanilla hand swap is suppressed client-side before its packet is ever sent, so this is not a
 * race: either this packet arrives or the vanilla one does, never both.
 */
public record ArtisanBoxSwapPacket() {
    public static void encode(ArtisanBoxSwapPacket packet, FriendlyByteBuf buffer) {
    }

    public static ArtisanBoxSwapPacket decode(FriendlyByteBuf buffer) {
        return new ArtisanBoxSwapPacket();
    }

    public static void handle(ArtisanBoxSwapPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            if (ArtisanHeartLogic.swapHotbarWithBox(sender)) {
                sender.containerMenu.broadcastChanges();
                sender.inventoryMenu.broadcastChanges();
                sender.level().playSound(null, sender.blockPosition(),
                        net.minecraft.sounds.SoundEvents.ENDER_CHEST_OPEN,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
            }
        });
        ctx.setPacketHandled(true);
    }
}
