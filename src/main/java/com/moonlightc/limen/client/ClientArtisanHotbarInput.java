package com.moonlightc.limen.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.hotbar.ArtisanHeartLogic;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import com.moonlightc.limen.network.ArtisanBoxSwapPacket;
import com.moonlightc.limen.network.ArtisanHotbarSelectPacket;
import com.moonlightc.limen.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client input for the artisan hotbar slots: scrolling past the end of the vanilla hotbar into them,
 * the optional direct-select keybinds, and intercepting the swap-offhand key when the offhand heart is
 * linked to a box.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ClientArtisanHotbarInput {
    private ClientArtisanHotbarInput() {
    }

    /**
     * Runs on the client tick, before {@code Minecraft.handleKeybinds} consumes the swap-offhand click
     * — verified against the vanilla {@code tick()} ordering, where {@code onPreClientTick} fires
     * first. Consuming the click here is what stops vanilla from ever sending its own swap packet.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }

        handleSwapOffhand(minecraft, player);
        handleVanillaHotbarKeys(minecraft, player);
        handleSlotKeys(player);
    }

    /**
     * Makes the vanilla number keys leave the artisan slots.
     *
     * <p>Vanilla's handler writes {@code inventory.selected = i} directly, with no event to hook. While
     * an artisan slot is mounted that write is invisible to the server whenever {@code i} happens to be
     * the slot the stack is mounted into — the value does not change, so no packet is sent and the
     * server's reconcile sees nothing to do. Scrolling rightwards off the last vanilla slot is exactly
     * how players enter the strip, which leaves the mount on slot 9 and makes the 9 key look dead.
     *
     * <p>So the press is consumed here, ahead of {@code Minecraft.handleKeybinds}, and turned into an
     * explicit "leave the artisan slots" request followed by the selection vanilla would have made.
     */
    private static void handleVanillaHotbarKeys(Minecraft minecraft, LocalPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        if (hotbars == null || !hotbars.hasArtisanSelected()) {
            return;
        }
        // In creative these keys double as the save/load-hotbar chords; those are vanilla's to handle.
        if (player.isCreative() && (minecraft.options.keySaveHotbarActivator.isDown()
                || minecraft.options.keyLoadHotbarActivator.isDown())) {
            return;
        }

        for (int index = 0; index < minecraft.options.keyHotbarSlots.length; index++) {
            if (!minecraft.options.keyHotbarSlots[index].consumeClick()) {
                continue;
            }
            requestSlot(player, ArtisanHotbars.NONE);
            player.getInventory().selected = index;
            return;
        }
    }

    /**
     * When a linked heart sits in the offhand, the swap key exchanges the hotbar with the box instead
     * of swapping hands. The vanilla keybind is consumed so its packet is never sent.
     */
    private static void handleSwapOffhand(Minecraft minecraft, LocalPlayer player) {
        if (!ArtisanHeartLogic.canSwapWithBox(player)) {
            return;
        }
        // consumeClick drains the queued press, which is exactly what keeps vanilla from acting on it.
        if (minecraft.options.keySwapOffhand.consumeClick()) {
            ModNetworking.CHANNEL.sendToServer(new ArtisanBoxSwapPacket());
        }
    }

    private static void handleSlotKeys(LocalPlayer player) {
        for (int slot = 0; slot < ArtisanHotbarKeys.SELECT_SLOT.length; slot++) {
            if (!ArtisanHotbarKeys.SELECT_SLOT[slot].consumeClick()) {
                continue;
            }
            ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
            // Pressing the key for the slot already selected returns to the vanilla hotbar, so one key
            // toggles in and out of its slot.
            int target = hotbars != null && hotbars.getSelected() == slot ? ArtisanHotbars.NONE : slot;
            requestSlot(player, target);
        }
    }

    /**
     * Extends the wheel across the artisan slots: scrolling right off vanilla slot 8 lands on the first
     * artisan slot and keeps going, then wraps back to vanilla slot 0.
     *
     * <p>Vanilla's own wheel handling is replaced outright rather than adjusted afterwards, so the
     * selection never visibly jumps: the event is cancelled and both the vanilla index and the artisan
     * selection are set here.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || player.isSpectator()) {
            return;
        }
        if (!ArtisanHeartLogic.hasHeart(player)) {
            return;
        }

        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }

        // Vanilla scrolls up to move left along the hotbar (selected -= signum), so follow that sign.
        int direction = delta > 0.0D ? -1 : 1;
        int current = ArtisanHeartLogic.currentPosition(player);
        int next = ArtisanHeartLogic.cyclePosition(player, current, direction);
        event.setCanceled(true);

        if (ArtisanHotbars.isArtisanPosition(next)) {
            requestSlot(player, next - ArtisanHotbars.VANILLA_SLOT_COUNT);
            return;
        }

        // Landing back on a vanilla slot: leave any artisan slot first, then set the vanilla index.
        // MultiPlayerGameMode.ensureHasSentCarriedItem notices the change and tells the server.
        requestSlot(player, ArtisanHotbars.NONE);
        player.getInventory().selected = next;
    }

    /**
     * Asks the server to select an artisan slot. The client does not move items itself: the hotbar is
     * server authoritative, and predicting the mount locally would fight the sync packet that follows.
     */
    private static void requestSlot(LocalPlayer player, int slot) {
        int sanitized = ArtisanHeartLogic.sanitizeSlot(player, slot);
        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        if (hotbars != null && hotbars.getSelected() == sanitized) {
            return;
        }
        ModNetworking.CHANNEL.sendToServer(new ArtisanHotbarSelectPacket(sanitized));
    }
}
