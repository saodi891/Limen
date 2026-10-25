package com.moonlightc.limen.client.gui;

import com.moonlightc.limen.inventory.CampfireMendMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Plain floating GUI for the campfire mend option: an item slot and an industrial-tape slot feeding a
 * result slot, laid out like an anvil, plus the player's armor column and offhand slot on the left so gear
 * can be stripped and fed in. Draws its own dark panel and slot boxes (no texture) to match the other
 * campfire sub-screens; the result is server-driven, so the screen only renders slots, title and a hint.
 */
public class CampfireMendScreen extends AbstractContainerScreen<CampfireMendMenu> {
    public CampfireMendScreen(CampfireMendMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
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
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CampfireMendMenu.BACK_BUTTON);
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
        for (int i = 0; i < 4; i++) {
            slotBox(graphics, 8, 18 + i * 18);
        }
        slotBox(graphics, 8, 96);
        slotBox(graphics, 62, 46);
        slotBox(graphics, 98, 46);
        slotBox(graphics, 140, 46);
        graphics.drawString(this.font, "+", this.leftPos + 84, this.topPos + 50, 0xFFFFFF, false);
        graphics.drawString(this.font, "=", this.leftPos + 124, this.topPos + 50, 0xFFFFFF, false);
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
        if (!this.menu.getSlot(CampfireMendMenu.RESULT_SLOT).hasItem()) {
            graphics.drawString(this.font, Component.translatable("gui.limen.campfire_mend.hint"),
                    8, 118, 0x909090, false);
        }
    }
}
