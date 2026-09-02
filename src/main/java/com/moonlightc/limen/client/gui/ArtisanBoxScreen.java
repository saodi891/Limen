package com.moonlightc.limen.client.gui;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.inventory.ArtisanBoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Artisan box screen. Same slot geometry as the shulker box screen, drawn with the mod's own
 * background so the box reads as its own container.
 */
public class ArtisanBoxScreen extends AbstractContainerScreen<ArtisanBoxMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE = Limen.id("textures/gui/container/artisan_box.png");

    public ArtisanBoxScreen(ArtisanBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // Vanilla's shulker box screen is one pixel taller than the default, which is what lines the
        // three content rows up with the background art.
        this.imageHeight += 1;
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
    }
}
