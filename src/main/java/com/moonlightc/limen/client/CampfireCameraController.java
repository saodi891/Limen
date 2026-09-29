package com.moonlightc.limen.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Client-side state for the campfire "peace scene" overhead camera. While the hub screen is open the
 * camera eases up to an isometric-leaning bird's-eye view centred on the campfire; when it closes the
 * camera snaps straight back to the player's own view. {@code CameraMixin} reads the interpolated
 * progress and pose each frame; {@link com.moonlightc.limen.event.CampfireCameraClientEvents} advances
 * the progress once per client tick and toggles the target on the menu open/close edges.
 */
public final class CampfireCameraController {
    /** Blocks above the campfire the camera ends up. */
    public static final float CAM_HEIGHT = 10.0F;
    /** Downward tilt from horizontal, isometric-leaning (0 = level, 90 = straight down). */
    public static final float CAM_PITCH = 40.0F;
    /** Diagonal facing so the scene reads as an isometric shot. */
    public static final float CAM_YAW = 45.0F;
    /** Length of the fast smooth pan, in client ticks. */
    private static final int TRANSITION_TICKS = 5;

    private static BlockPos campfirePos;
    private static boolean targetActive;
    private static int ticks;
    private static int prevTicks;

    private CampfireCameraController() {
    }

    public static void setActive(boolean active, BlockPos pos) {
        targetActive = active;
        if (active && pos != null) {
            campfirePos = pos;
        }
        // Exit resets the camera immediately: snap the pan progress to zero instead of easing back,
        // so pressing 退出 (or any close) returns to the player's own view on the same frame.
        if (!active) {
            ticks = 0;
            prevTicks = 0;
        }
    }

    public static void tick() {
        prevTicks = ticks;
        if (targetActive) {
            ticks = Mth.clamp(ticks + 1, 0, TRANSITION_TICKS);
        }
    }

    public static BlockPos getCampfirePos() {
        return campfirePos;
    }

    /** True while the overhead scene is engaged, i.e. the camera is (easing to) the bird's-eye pose. */
    public static boolean isActive() {
        return targetActive;
    }

    /** Smoothstepped 0..1 pan progress for the given render partial tick. */
    public static float getEasedProgress(float partialTick) {
        float raw = Mth.clamp(Mth.lerp(partialTick, prevTicks, ticks) / TRANSITION_TICKS, 0.0F, 1.0F);
        return raw * raw * (3.0F - 2.0F * raw);
    }
}
