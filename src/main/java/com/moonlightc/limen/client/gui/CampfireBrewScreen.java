package com.moonlightc.limen.client.gui;

import com.moonlightc.limen.inventory.CampfireBrewMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Plain floating GUI for the campfire brew option: a potion slot and an arrow slot feeding a result slot,
 * laid out like an anvil. Draws its own dark panel and slot boxes (no texture) to match the other campfire
 * sub-screens; the result is server-driven, so the screen only renders slots, title and a hint.
 */
public class CampfireBrewScreen extends AbstractContainerScreen<CampfireBrewMenu> {
    public CampfireBrewScreen(CampfireBrewMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.limen.campfire_back"), b -> onBack())
                .bounds(this.leftPos + this.imageWidth - 50, this.topPos + 4, 44, 16)
                .build());
    }

    private void onBack() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CampfireBrewMenu.BACK_BUTTON);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight,
                0xC0101010);
        slotBox(graphics, 44, 35);
        slotBox(graphics, 80, 35);
        slotBox(graphics, 124, 35);
        graphics.drawString(this.font, "+", this.leftPos + 66, this.topPos + 39, 0xFFFFFF, false);
        graphics.drawString(this.font, "=", this.leftPos + 108, this.topPos + 39, 0xFFFFFF, false);
    }

    private void slotBox(GuiGraphics graphics, int x, int y) {
        int sx = this.leftPos + x;
        int sy = this.topPos + y;
        graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
        graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF373737);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, 0xFFFFFF, false);
        if (!this.menu.getSlot(CampfireBrewMenu.RESULT_SLOT).hasItem()) {
            graphics.drawString(this.font, Component.translatable("gui.limen.campfire_brew.hint"),
                    8, 20, 0x909090, false);
        }
    }
}
