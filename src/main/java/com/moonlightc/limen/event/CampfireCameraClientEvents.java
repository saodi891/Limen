package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.client.CampfireCameraController;
import com.moonlightc.limen.inventory.CampfireScene;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the overhead campfire camera purely from client state: the open campfire menu (a
 * {@link CampfireScene}) already carries the campfire position, so the camera can arm itself whenever
 * such a menu is the open container and disarm when it closes, no extra packet required.
 *
 * <p>While the scene is active the view is forced into third person so the player's own body renders,
 * standing in place at the campfire — the {@code CameraMixin} then lifts the camera overhead while the
 * player model stays put. The previous camera type is restored on close.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class CampfireCameraClientEvents {
    private static boolean wasActive;
    private static CameraType previousCameraType;

    private CampfireCameraClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        boolean active = player != null && player.containerMenu instanceof CampfireScene;
        BlockPos pos = active ? ((CampfireScene) player.containerMenu).getCampfirePos() : null;

        if (active && !wasActive) {
            previousCameraType = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!active && wasActive) {
            minecraft.options.setCameraType(previousCameraType != null ? previousCameraType : CameraType.FIRST_PERSON);
            previousCameraType = null;
        }
        wasActive = active;

        CampfireCameraController.setActive(active, pos);
        CampfireCameraController.tick();
    }
}
