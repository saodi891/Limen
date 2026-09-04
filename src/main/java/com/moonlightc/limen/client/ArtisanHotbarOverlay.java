package com.moonlightc.limen.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.hotbar.ArtisanHeartLogic;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the three artisan hotbar slots immediately to the right of the vanilla hotbar while a heart is
 * carried, with the vanilla selection frame on whichever one is selected.
 *
 * <p>Offsets mirror {@code Gui.renderHotbar}: the strip is centred on {@code width/2 - 91} and items
 * sit at {@code centre - 90 + slot * 20}. Continuing that arithmetic past slot 8 is what makes the
 * extra slots line up seamlessly with the vanilla bar.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ArtisanHotbarOverlay {
    /**
     * Vanilla's widgets.png layout, so a texture only needs to match the vanilla sprite sheet: the
     * hotbar strip is 182x22 at (0,0) and the selection frame is 24x24 at (0,22).
     */
    private static final ResourceLocation TEXTURE = Limen.id("textures/gui/artisan_hotbar.png");
    /** Vanilla's own sheet, for the replacement hotbar drawn when an artisan slot is selected. */
    private static final ResourceLocation VANILLA_WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    /** Vanilla's icon sheet, for the attack-strength indicator the replacement hotbar has to redraw. */
    private static final ResourceLocation VANILLA_ICONS = new ResourceLocation("textures/gui/icons.png");

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 22;
    private static final int FRAME_WIDTH = 24;
    /** Vanilla blits only 22 of the frame sprite's 24 rows; drawing 24 adds a stray line below the bar. */
    private static final int FRAME_HEIGHT = 22;
    private static final int FRAME_V = 22;
    private static final int SLOT_WIDTH = 20;
    /** Width of the artisan strip: three slots plus the strip's own one-pixel border on each side. */
    private static final int STRIP_WIDTH = ArtisanHotbars.SLOT_COUNT * SLOT_WIDTH + 2;
    /** Vanilla's own item inset within the strip: +2 across, and 16+3 up from the screen bottom. */
    private static final int ITEM_INSET_X = 2;
    private static final int ITEM_OFFSET_FROM_BOTTOM = 16 + 3;
    /** Gap between the vanilla bar and the artisan strip, matching vanilla's offhand box spacing. */
    private static final int STRIP_GAP = 4;
    /** Width vanilla's offhand box occupies beyond the end of the hotbar strip. */
    private static final int OFFHAND_BOX_WIDTH = 29;
    /** The offhand box really is 24 tall, unlike the selection frame vanilla clips to 22. */
    private static final int OFFHAND_BOX_HEIGHT = 24;

    private ArtisanHotbarOverlay() {
    }

    @Mod.EventBusSubscriber(modid = Limen.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "artisan_hotbars",
                    (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
        }
    }

    /**
     * Replaces vanilla's hotbar while an artisan slot is selected.
     *
     * <p>Vanilla draws its selection frame unconditionally at {@code inventory.selected}, and the
     * selected artisan stack is physically mounted into that slot — so left alone, the mounted item
     * and a selection frame appear in the vanilla bar at the same time as in the artisan strip. There
     * is no way to suppress just the frame: {@code renderSlot} is private and the frame position is
     * derived inside {@code renderHotbar}. So the whole bar is redrawn here instead, showing the
     * player's own stashed stack in that slot and leaving the frame to the artisan strip.
     */
    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }

        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        if (hotbars == null || !hotbars.hasArtisanSelected() || hotbars.getStashIndex() == ArtisanHotbars.NONE) {
            return;
        }

        event.setCanceled(true);
        renderVanillaBar(event.getGuiGraphics(), player, hotbars,
                event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(),
                event.getPartialTick());
    }

    /** Vanilla's own hotbar, minus the selection frame, with the mounted slot showing its real stack. */
    private static void renderVanillaBar(GuiGraphics graphics, LocalPlayer player, ArtisanHotbars hotbars,
                                         int screenWidth, int screenHeight, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack offhand = player.getOffhandItem();
        HumanoidArm offhandSide = player.getMainArm().getOpposite();
        int centre = screenWidth / 2;
        int barLeft = centre - 91;
        int barTop = screenHeight - BAR_HEIGHT;

        // The -90 z translation is vanilla's: it puts the bar behind the items drawn afterwards.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, -90.0F);
        graphics.blit(VANILLA_WIDGETS, barLeft, barTop, 0, 0, BAR_WIDTH, BAR_HEIGHT);
        if (!offhand.isEmpty()) {
            if (offhandSide == HumanoidArm.LEFT) {
                graphics.blit(VANILLA_WIDGETS, centre - 91 - OFFHAND_BOX_WIDTH, screenHeight - 23,
                        24, FRAME_V, OFFHAND_BOX_WIDTH, OFFHAND_BOX_HEIGHT);
            } else {
                graphics.blit(VANILLA_WIDGETS, centre + 91, screenHeight - 23,
                        53, FRAME_V, OFFHAND_BOX_WIDTH, OFFHAND_BOX_HEIGHT);
            }
        }
        graphics.pose().popPose();

        int itemY = screenHeight - ITEM_OFFSET_FROM_BOTTOM;
        int seed = 1;
        for (int slot = 0; slot < ArtisanHotbars.VANILLA_SLOT_COUNT; slot++) {
            // getVanillaStack resolves the mounted slot back to the player's own stack.
            ItemStack stack = hotbars.getVanillaStack(player, slot);
            renderStack(graphics, minecraft, player, stack,
                    centre - 90 + slot * SLOT_WIDTH + ITEM_INSET_X, itemY, seed++, partialTick);
        }
        if (!offhand.isEmpty()) {
            int offhandX = offhandSide == HumanoidArm.LEFT ? centre - 91 - 26 : centre + 91 + 10;
            renderStack(graphics, minecraft, player, offhand, offhandX, itemY, seed, partialTick);
        }

        renderAttackIndicator(graphics, minecraft, offhandSide, centre, screenHeight);
    }

    /**
     * Vanilla's attack-strength bar, which lives at the end of {@code renderHotbar} and would otherwise
     * vanish for as long as an artisan slot is selected.
     */
    private static void renderAttackIndicator(GuiGraphics graphics, Minecraft minecraft,
                                              HumanoidArm offhandSide, int centre, int screenHeight) {
        RenderSystem.enableBlend();
        if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float scale = minecraft.player.getAttackStrengthScale(0.0F);
            if (scale < 1.0F) {
                int y = screenHeight - 20;
                int x = offhandSide == HumanoidArm.RIGHT ? centre - 91 - 22 : centre + 91 + 6;
                int filled = (int) (scale * 19.0F);
                graphics.blit(VANILLA_ICONS, x, y, 0, 94, 18, 18);
                graphics.blit(VANILLA_ICONS, x, y + 18 - filled, 18, 112 - filled, 18, filled);
            }
        }
        RenderSystem.disableBlend();
    }

    private static void renderStack(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player,
                                    ItemStack stack, int x, int y, int seed, float partialTick) {
        if (stack.isEmpty()) {
            return;
        }

        // Vanilla's pop animation, copied from the private Gui.renderSlot: a freshly picked-up item
        // bounces once. Skipping it would make the redrawn bar visibly stiffer than the real one.
        float pop = stack.getPopTime() - partialTick;
        boolean popping = pop > 0.0F;
        if (popping) {
            float bounce = 1.0F + pop / 5.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(x + 8.0F, y + 12.0F, 0.0F);
            graphics.pose().scale(1.0F / bounce, (bounce + 1.0F) / 2.0F, 1.0F);
            graphics.pose().translate(-(x + 8.0F), -(y + 12.0F), 0.0F);
        }

        graphics.renderItem(player, stack, x, y, seed);
        if (popping) {
            graphics.pose().popPose();
        }
        graphics.renderItemDecorations(minecraft.font, stack, x, y);
    }

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        if (!ArtisanHeartLogic.hasHeart(player)) {
            return;
        }

        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        if (hotbars == null) {
            return;
        }

        int centre = screenWidth / 2;
        int stripLeft = stripLeft(player, centre, screenWidth);
        int stripTop = screenHeight - BAR_HEIGHT;

        // Cut the strip out of the same 182-wide sprite, so the left border, slot dividers and right
        // border all come from the vanilla art without needing a separate sprite.
        graphics.blit(TEXTURE, stripLeft, stripTop, 0, 0, STRIP_WIDTH - 1, BAR_HEIGHT);
        graphics.blit(TEXTURE, stripLeft + STRIP_WIDTH - 1, stripTop, BAR_WIDTH - 1, 0, 1, BAR_HEIGHT);

        int itemY = screenHeight - ITEM_OFFSET_FROM_BOTTOM;
        for (int slot = 0; slot < ArtisanHotbars.SLOT_COUNT; slot++) {
            ItemStack stack = hotbars.getArtisanStack(player, slot);
            if (stack.isEmpty()) {
                continue;
            }
            int x = stripLeft + 1 + slot * SLOT_WIDTH + ITEM_INSET_X;
            graphics.renderItem(stack, x, itemY);
            graphics.renderItemDecorations(minecraft.font, stack, x, itemY);
        }

        if (hotbars.hasArtisanSelected()) {
            // Vanilla offsets the frame one pixel left of the bar so the 24-wide sprite centres over the
            // 20-wide cell; the strip has to repeat that or the frame sits a pixel off its slot.
            int frameLeft = stripLeft - 1 + hotbars.getSelected() * SLOT_WIDTH;
            graphics.blit(TEXTURE, frameLeft, screenHeight - 23, 0, FRAME_V, FRAME_WIDTH, FRAME_HEIGHT);
        }
    }

    /**
     * Places the strip on the side the offhand box is not on, so the two never overlap. Vanilla draws
     * the offhand box on the opposite side from the main arm.
     *
     * <p>Falls back to the other side of the vanilla bar when the preferred side would run off the
     * screen, which happens at large GUI scales on a narrow window.
     */
    private static int stripLeft(LocalPlayer player, int centre, int screenWidth) {
        boolean offhandOnRight = player.getMainArm().getOpposite() == HumanoidArm.RIGHT;
        // The offhand box occupies centre + 91 .. + 120 on whichever side it is drawn.
        int right = offhandOnRight ? centre + 91 + OFFHAND_BOX_WIDTH + STRIP_GAP : centre + 91 + STRIP_GAP;
        if (right + STRIP_WIDTH <= screenWidth) {
            return right;
        }

        int left = offhandOnRight
                ? centre - 91 - STRIP_GAP - STRIP_WIDTH
                : centre - 91 - OFFHAND_BOX_WIDTH - STRIP_GAP - STRIP_WIDTH;
        return left >= 0 ? left : right;
    }
}
