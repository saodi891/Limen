package com.moonlightc.limen.client.gui;

import com.moonlightc.limen.client.CampfireCameraController;
import com.moonlightc.limen.inventory.CampfireHubMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Settings-like overlay for the campfire options hub. Draws no container texture or labels — just a
 * 3x2 grid of buttons that fades in together with the overhead camera pan (both driven by
 * {@link CampfireCameraController}). Rest is handled server-side via a menu button click; Exit closes
 * the screen (which resets the camera immediately); the other four buttons are placeholders.
 */
public class CampfireHubScreen extends AbstractContainerScreen<CampfireHubMenu> {
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP_X = 8;
    private static final int GAP_Y = 6;

    public CampfireHubScreen(CampfireHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int totalHeight = BUTTON_HEIGHT * 3 + GAP_Y * 2;
        // Sit the grid in the upper quarter of the screen rather than dead-center, so it clears the campfire
        // scene below it.
        int startY = this.height / 4 - totalHeight / 2;
        int leftX = this.width / 2 - GAP_X / 2 - BUTTON_WIDTH;
        int rightX = this.width / 2 + GAP_X / 2;

        addRow(leftX, rightX, startY,
                Component.translatable("gui.limen.campfire_hub.rest"), CampfireHubMenu.BUTTON_REST, true,
                Component.translatable("gui.limen.campfire_hub.exit"), CampfireHubMenu.BUTTON_EXIT, true);
        addRow(leftX, rightX, startY + BUTTON_HEIGHT + GAP_Y,
                Component.translatable("gui.limen.campfire_hub.weave"), CampfireHubMenu.BUTTON_WEAVE, false,
                Component.translatable("gui.limen.campfire_hub.mend"), CampfireHubMenu.BUTTON_MEND, false);
        addRow(leftX, rightX, startY + (BUTTON_HEIGHT + GAP_Y) * 2,
                Component.translatable("gui.limen.campfire_hub.enchant"), CampfireHubMenu.BUTTON_ENCHANT, false,
                Component.translatable("gui.limen.campfire_hub.brew"), CampfireHubMenu.BUTTON_BREW, false);
    }

    private void addRow(int leftX, int rightX, int y,
                        Component leftLabel, int leftId, boolean leftEnabled,
                        Component rightLabel, int rightId, boolean rightEnabled) {
        this.addRenderableWidget(makeButton(leftX, y, leftLabel, leftId, leftEnabled));
        this.addRenderableWidget(makeButton(rightX, y, rightLabel, rightId, rightEnabled));
    }

    private Button makeButton(int x, int y, Component label, int id, boolean enabled) {
        Button button = Button.builder(label, b -> onButton(id))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        button.active = enabled;
        return button;
    }

    private void onButton(int id) {
        if (id == CampfireHubMenu.BUTTON_EXIT) {
            this.onClose();
            return;
        }
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float alpha = CampfireCameraController.getEasedProgress(partialTick);
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget) {
                widget.setAlpha(alpha);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // No container texture: the hub is a floating button grid over the world/camera scene.
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // No title or inventory labels.
    }
}
