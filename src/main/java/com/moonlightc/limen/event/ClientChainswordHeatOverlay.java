package com.moonlightc.limen.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.ChainswordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the chainsword's heat as a vanilla-style item-cooldown overlay on its hotbar slot: a white
 * bar rising from the bottom of the icon that fills as heat accumulates, matching how vanilla shows
 * item cooldowns. The real overheat cooldown is a genuine item cooldown, so vanilla draws that one.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ClientChainswordHeatOverlay {
    private ClientChainswordHeatOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }

        float progress = ClientChainswordEvents.getHeatProgress();
        if (progress <= 0.0F) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof ChainswordItem)) {
            return;
        }

        // Locate the selected hotbar slot the same way vanilla's hotbar renderer does.
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int hotbarLeft = screenWidth / 2 - 91;
        int slotX = hotbarLeft + player.getInventory().selected * 20 + 3;
        int slotY = screenHeight - 16 - 3;

        int barHeight = Math.round(16.0F * progress);
        if (barHeight <= 0) {
            return;
        }

        RenderSystem.enableBlend();
        graphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),
                slotX, slotY + (16 - barHeight), slotX + 16, slotY + 16, 0x7FFFFFFF);
        RenderSystem.disableBlend();
    }
}
