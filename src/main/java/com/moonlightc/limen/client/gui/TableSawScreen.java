package com.moonlightc.limen.client.gui;

import com.moonlightc.limen.inventory.TableSawMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class TableSawScreen extends AbstractContainerScreen<TableSawMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/stonecutter.png");
    private static final int COLUMNS = 4;
    private static final int VISIBLE_ROWS = 3;
    private static final int VISIBLE_RECIPES = COLUMNS * VISIBLE_ROWS;
    private float scrollOffset;
    private boolean scrolling;
    private int startIndex;
    private boolean displayRecipes;

    public TableSawScreen(TableSawMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        menu.registerUpdateListener(this::containerChanged);
        this.titleLabelY--;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        this.renderBackground(graphics);
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        int scrollPixels = (int) (41.0F * this.scrollOffset);
        graphics.blit(BACKGROUND, this.leftPos + 119, this.topPos + 15 + scrollPixels,
                176 + (this.isScrollBarActive() ? 0 : 12), 0, 12, 15);

        int recipeX = this.leftPos + 52;
        int recipeY = this.topPos + 14;
        int endIndex = this.startIndex + VISIBLE_RECIPES;
        this.renderButtons(graphics, mouseX, mouseY, recipeX, recipeY, endIndex);
        this.renderRecipes(graphics, recipeX, recipeY, endIndex);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (!this.displayRecipes) {
            return;
        }
        int recipeX = this.leftPos + 52;
        int recipeY = this.topPos + 14;
        int endIndex = Math.min(this.startIndex + VISIBLE_RECIPES, this.menu.getNumRecipes());
        for (int index = this.startIndex; index < endIndex; index++) {
            int relative = index - this.startIndex;
            int x = recipeX + relative % COLUMNS * 16;
            int y = recipeY + relative / COLUMNS * 18 + 2;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 18) {
                graphics.renderTooltip(this.font, this.menu.getOfferResult(index), mouseX, mouseY);
            }
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY,
                               int recipeX, int recipeY, int endIndex) {
        for (int index = this.startIndex; index < endIndex && index < this.menu.getNumRecipes(); index++) {
            int relative = index - this.startIndex;
            int x = recipeX + relative % COLUMNS * 16;
            int y = recipeY + relative / COLUMNS * 18 + 2;
            int textureV = this.imageHeight;
            if (index == this.menu.getSelectedRecipeIndex()) {
                textureV += 18;
            } else if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 18) {
                textureV += 36;
            }
            graphics.blit(BACKGROUND, x, y - 1, 0, textureV, 16, 18);
        }
    }

    private void renderRecipes(GuiGraphics graphics, int recipeX, int recipeY, int endIndex) {
        for (int index = this.startIndex; index < endIndex && index < this.menu.getNumRecipes(); index++) {
            int relative = index - this.startIndex;
            int x = recipeX + relative % COLUMNS * 16;
            int y = recipeY + relative / COLUMNS * 18 + 2;
            ItemStack result = this.menu.getOfferResult(index);
            graphics.renderItem(result, x, y);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.scrolling = false;
        if (this.displayRecipes) {
            int recipeX = this.leftPos + 52;
            int recipeY = this.topPos + 14;
            int endIndex = Math.min(this.startIndex + VISIBLE_RECIPES, this.menu.getNumRecipes());
            for (int index = this.startIndex; index < endIndex; index++) {
                int relative = index - this.startIndex;
                double x = mouseX - (recipeX + relative % COLUMNS * 16);
                double y = mouseY - (recipeY + relative / COLUMNS * 18);
                if (x >= 0.0D && y >= 0.0D && x < 16.0D && y < 18.0D
                        && this.menu.clickMenuButton(this.minecraft.player, index)) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
                    return true;
                }
            }
            int scrollX = this.leftPos + 119;
            int scrollY = this.topPos + 9;
            if (mouseX >= scrollX && mouseX < scrollX + 12
                    && mouseY >= scrollY && mouseY < scrollY + 54) {
                this.scrolling = true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling && this.isScrollBarActive()) {
            int top = this.topPos + 14;
            int bottom = top + 54;
            this.scrollOffset = Mth.clamp(((float) mouseY - top - 7.5F) / (bottom - top - 15.0F), 0.0F, 1.0F);
            this.startIndex = Math.round(this.scrollOffset * this.getOffscreenRows()) * COLUMNS;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.isScrollBarActive()) {
            int rows = this.getOffscreenRows();
            this.scrollOffset = Mth.clamp(this.scrollOffset - (float) delta / rows, 0.0F, 1.0F);
            this.startIndex = Math.round(this.scrollOffset * rows) * COLUMNS;
        }
        return true;
    }

    private boolean isScrollBarActive() {
        return this.displayRecipes && this.menu.getNumRecipes() > VISIBLE_RECIPES;
    }

    private int getOffscreenRows() {
        return (this.menu.getNumRecipes() + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS;
    }

    private void containerChanged() {
        this.displayRecipes = this.menu.hasInputItem();
        if (!this.displayRecipes) {
            this.scrollOffset = 0.0F;
            this.startIndex = 0;
        } else if (this.startIndex >= this.menu.getNumRecipes()) {
            this.scrollOffset = 0.0F;
            this.startIndex = 0;
        }
    }
}


