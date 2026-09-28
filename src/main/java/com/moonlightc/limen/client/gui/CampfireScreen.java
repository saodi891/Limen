package com.moonlightc.limen.client.gui;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.inventory.CampfireMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Roasting screen shared by the campfire and the soul campfire. The only difference between the two
 * is the workstation name in the top-left corner, which arrives as the menu title, so one screen and
 * one background serve both blocks.
 */
public class CampfireScreen extends AbstractContainerScreen<CampfireMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE = Limen.id("textures/gui/container/campfire.png");

    /** Roast bar overlay, kept to the right of the 176-wide background on the same 256x256 sheet. */
    private static final int BAR_U = 176;
    private static final int BAR_V = 0;
    private static final int BAR_WIDTH = 16;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_Y = 42;

    public CampfireScreen(CampfireMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 153;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(CONTAINER_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        for (int slot = 0; slot < CampfireMenu.ROAST_SLOTS; slot++) {
            int filled = this.menu.getRoastTicks(slot) * BAR_WIDTH / CampfireMenu.ROAST_TICKS;
            if (filled <= 0) {
                continue;
            }
            int x = this.leftPos + CampfireMenu.SLOT_ROW_X + slot * CampfireMenu.SLOT_PITCH;
            graphics.blit(CONTAINER_TEXTURE, x, this.topPos + BAR_Y, BAR_U, BAR_V, filled, BAR_HEIGHT);
        }
    }
}
