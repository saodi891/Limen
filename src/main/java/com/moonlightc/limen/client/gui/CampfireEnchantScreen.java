package com.moonlightc.limen.client.gui;

import com.moonlightc.limen.inventory.CampfireEnchantMenu;
import com.moonlightc.limen.inventory.CampfireEnchantMenu.EnchantOffer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain floating GUI for the campfire enchant option: an input slot for the weapon, a column of the
 * tier's random enchant offers (each showing its level and point cost), a reroll button, and the
 * player's current enchantment-point count. Offers are rebuilt whenever the input, tier or seed change;
 * clicking one sends its index as a menu button, and reroll sends {@link CampfireEnchantMenu#REROLL_BUTTON}.
 */
public class CampfireEnchantScreen extends AbstractContainerScreen<CampfireEnchantMenu> {
    private static final int BUTTON_X = 8;
    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 18;
    private static final int GRID_TOP = 44;
    private static final int GRID_STEP = 20;
    private static final int REROLL_COST = 1;

    private final List<Button> offerButtons = new ArrayList<>();
    private Button rerollButton;
    private List<EnchantOffer> offers = List.of();
    private ItemStack lastInput = ItemStack.EMPTY;
    private int lastTier = Integer.MIN_VALUE;
    private int lastSeed = Integer.MIN_VALUE;

    public CampfireEnchantScreen(CampfireEnchantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.limen.campfire_back"), b -> onClick(CampfireEnchantMenu.BACK_BUTTON))
                .bounds(this.leftPos + this.imageWidth - 50, this.topPos + 4, 44, 16)
                .build());
        rebuildOffers();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack input = this.menu.getInput();
        if (!ItemStack.isSameItemSameTags(input, this.lastInput)
                || this.menu.getTier() != this.lastTier
                || this.menu.getSeed() != this.lastSeed) {
            rebuildOffers();
        }
        refreshActive();
    }

    private void refreshActive() {
        int points = this.menu.getEnchantmentPoints();
        for (int i = 0; i < this.offerButtons.size(); i++) {
            this.offerButtons.get(i).active = points >= this.offers.get(i).cost();
        }
        if (this.rerollButton != null) {
            this.rerollButton.active = !this.offers.isEmpty() && points >= REROLL_COST;
        }
    }

    private void rebuildOffers() {
        for (Button button : this.offerButtons) {
            this.removeWidget(button);
        }
        this.offerButtons.clear();
        if (this.rerollButton != null) {
            this.removeWidget(this.rerollButton);
            this.rerollButton = null;
        }

        ItemStack input = this.menu.getInput();
        this.lastInput = input.copy();
        this.lastTier = this.menu.getTier();
        this.lastSeed = this.menu.getSeed();
        this.offers = CampfireEnchantMenu.computeOffers(input, this.lastTier, this.lastSeed);

        for (int i = 0; i < this.offers.size(); i++) {
            EnchantOffer offer = this.offers.get(i);
            int y = this.topPos + GRID_TOP + i * GRID_STEP;
            int id = i;
            Component label = Component.translatable("gui.limen.campfire_enchant.offer",
                    offer.enchantment().getFullname(offer.level()), offer.cost());
            Button button = Button.builder(label, b -> onClick(id))
                    .bounds(this.leftPos + BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            this.offerButtons.add(button);
            this.addRenderableWidget(button);
        }

        if (!this.offers.isEmpty()) {
            int y = this.topPos + GRID_TOP + this.offers.size() * GRID_STEP + 4;
            Component label = Component.translatable("gui.limen.campfire_enchant.reroll", REROLL_COST);
            this.rerollButton = Button.builder(label, b -> onClick(CampfireEnchantMenu.REROLL_BUTTON))
                    .bounds(this.leftPos + BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            this.addRenderableWidget(this.rerollButton);
        }
        refreshActive();
    }

    private void onClick(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
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
        int slotX = this.leftPos + 80;
        int slotY = this.topPos + 20;
        graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF8B8B8B);
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF373737);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, 0xFFFFFF, false);
        Component points = Component.translatable("gui.limen.campfire_enchant.points", this.menu.getEnchantmentPoints());
        graphics.drawString(this.font, points, 8, 18, 0xA0E0FF, false);
        if (this.offers.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.limen.campfire_enchant.hint"),
                    8, GRID_TOP, 0x909090, false);
        }
    }
}
