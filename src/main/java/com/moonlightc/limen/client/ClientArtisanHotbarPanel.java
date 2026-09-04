package com.moonlightc.limen.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.hotbar.ArtisanHeartLogic;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import com.moonlightc.limen.network.ArtisanHotbarSlotClickPacket;
import com.moonlightc.limen.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Adds the three artisan hotbar slots to the player's inventory screen so their contents can be
 * configured, placed directly below the hotbar row and aligned with its left edge.
 *
 * <p>The position is derived from the screen's own hotbar slots rather than hard-coded, so it lands
 * correctly in both the survival and creative inventory screens even though their backgrounds are
 * different sizes.
 *
 * <p>Drawn from {@link ContainerScreenEvent.Render.Foreground} because that fires inside the pose
 * translated to the GUI's top-left corner, the same space vanilla's own slot rendering uses — so the
 * coordinates here read exactly like slot coordinates.
 *
 * <p>Clicks are handled by a packet rather than by real menu slots. Adding slots to the vanilla
 * inventory menu would change its slot count, which every vanilla container packet encodes by index;
 * a mod-side panel avoids touching that.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ClientArtisanHotbarPanel {
    private static final ResourceLocation TEXTURE = Limen.id("textures/gui/artisan_hotbar.png");
    /** Slot background cut from the vanilla hotbar strip, whose inner cell is 18x18 at (1,1). */
    private static final int SLOT_U = 1;
    private static final int SLOT_V = 1;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PITCH = 18;
    /** Vertical gap between the hotbar row and the artisan panel below it. */
    private static final int PANEL_GAP = 4;
    /** Height of the selected-slot underline, which has to stay on screen along with the slot. */
    private static final int UNDERLINE_HEIGHT = 2;

    private ClientArtisanHotbarPanel() {
    }

    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ArtisanHeartLogic.hasHeart(player)) {
            return;
        }

        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        Anchor anchor = anchor(screen, player);
        if (hotbars == null || anchor == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int localMouseX = event.getMouseX() - screen.getGuiLeft();
        int localMouseY = event.getMouseY() - screen.getGuiTop();

        for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
            int x = anchor.slotX(slot);
            int y = anchor.y();
            graphics.blit(TEXTURE, x, y, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE);

            ItemStack stack = hotbars.getArtisanStack(player, slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(Minecraft.getInstance().font, stack, x + 1, y + 1);
            }

            if (anchor.isOver(localMouseX, localMouseY, slot)) {
                AbstractContainerScreen.renderSlotHighlight(graphics, x + 1, y + 1, 0);
            }
            if (slot == hotbars.getSelected()) {
                // Two-pixel underline marks the selected slot, echoing the HUD frame.
                graphics.fill(x, y + SLOT_SIZE, x + SLOT_SIZE, y + SLOT_SIZE + UNDERLINE_HEIGHT, 0xFFFFFFFF);
            }
        }
    }

    /** Tooltips have to come after the foreground pass, outside the GUI-local pose. */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ArtisanHeartLogic.hasHeart(player)) {
            return;
        }

        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        if (hotbars == null || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }

        int slot = findSlot(screen, player, event.getMouseX(), event.getMouseY());
        if (slot < 0) {
            return;
        }

        ItemStack stack = hotbars.getArtisanStack(player, slot);
        if (stack.isEmpty()) {
            List<Component> lines = List.of(
                    Component.translatable("tooltip.limen.artisan_hotbar.slot", slot + 1));
            event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font, lines,
                    event.getMouseX(), event.getMouseY());
            return;
        }
        event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, stack,
                event.getMouseX(), event.getMouseY());
    }

    /**
     * Intercepts clicks on the panel before the screen can treat them as clicks outside the GUI, which
     * vanilla reads as "throw the carried stack away".
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen raw = event.getScreen();
        if (!(raw instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        // Left and right only. Middle click is vanilla's creative-clone button and has no meaning here,
        // so it is left alone rather than silently treated as a plain take.
        if (event.getButton() != 0 && event.getButton() != 1) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ArtisanHeartLogic.hasHeart(player)) {
            return;
        }

        int slot = findSlot(screen, player, (int) event.getMouseX(), (int) event.getMouseY());
        if (slot < 0) {
            return;
        }

        // Right click splits or places one, matching vanilla slot behaviour.
        boolean splitStack = event.getButton() == 1;
        ModNetworking.CHANNEL.sendToServer(new ArtisanHotbarSlotClickPacket(slot, splitStack));
        event.setCanceled(true);
    }

    /** Artisan slot under the given screen coordinates, or -1 when the cursor is elsewhere. */
    private static int findSlot(AbstractContainerScreen<?> screen, LocalPlayer player, int mouseX, int mouseY) {
        Anchor anchor = anchor(screen, player);
        if (anchor == null) {
            return -1;
        }

        int localMouseX = mouseX - screen.getGuiLeft();
        int localMouseY = mouseY - screen.getGuiTop();
        for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
            if (anchor.isOver(localMouseX, localMouseY, slot)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Locates the screen's own hotbar row and returns the panel position just below it, or null when
     * this screen shows no hotbar — a chest or furnace, where the panel has no business appearing.
     *
     * <p>Reading the row off the live slots rather than hard-coding coordinates is what keeps the panel
     * aligned across the survival and creative screens, whose backgrounds differ in size and put the
     * hotbar at different heights.
     */
    @Nullable
    private static Anchor anchor(AbstractContainerScreen<?> screen, LocalPlayer player) {
        int left = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Slot slot : screen.getMenu().slots) {
            // The hotbar is exactly the first nine slots of the player's own inventory.
            if (slot.container != player.getInventory() || slot.getContainerSlot() >= ArtisanHotbars.VANILLA_SLOT_COUNT) {
                continue;
            }
            left = Math.min(left, slot.x);
            bottom = Math.max(bottom, slot.y);
        }

        if (left == Integer.MAX_VALUE) {
            return null;
        }

        // The row sits inside the background, so the panel below it overhangs the bottom edge. That is
        // intended, but at large GUI scales the overhang can leave the window, so it is clamped back on.
        int y = bottom + SLOT_SIZE + PANEL_GAP;
        int lowest = screen.height - screen.getGuiTop() - SLOT_SIZE - UNDERLINE_HEIGHT;
        return new Anchor(left, Math.min(y, lowest));
    }

    /** Panel origin in GUI-local coordinates: the hotbar row's left edge, just below the row. */
    private record Anchor(int x, int y) {
        int slotX(int slot) {
            return this.x + slot * SLOT_PITCH;
        }

        boolean isOver(int localMouseX, int localMouseY, int slot) {
            int slotX = slotX(slot);
            return localMouseX >= slotX && localMouseX < slotX + SLOT_SIZE
                    && localMouseY >= this.y && localMouseY < this.y + SLOT_SIZE;
        }
    }
}
