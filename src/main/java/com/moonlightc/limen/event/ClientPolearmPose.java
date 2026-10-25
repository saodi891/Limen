package com.moonlightc.limen.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.PoweredPolearmItem;
import com.moonlightc.limen.item.RoyalPolearmItem;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-person charge ("held forward") brace for the royal / powered polearm. The third-person aim that tracks
 * the view lives in {@code PolearmArmPoseMixin} (it must run at the tail of the model's setupAnim to survive
 * vanilla's per-frame arm reset); this only braces the first-person arm so the weapon reads as held forward
 * while charging rather than resting in the idle hand pose. Only the main hand is posed, matching the
 * one-handed spear grip.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ClientPolearmPose {
    private ClientPolearmPose() {
    }

    private static boolean isPolearm(ItemStack stack) {
        return stack.getItem() instanceof RoyalPolearmItem || stack.getItem() instanceof PoweredPolearmItem;
    }

    /** True only while the player is actively charging a polearm (holding right-click). */
    private static boolean shouldPose(Player player) {
        return player.isUsingItem() && isPolearm(player.getUseItem());
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        Player player = event.getPlayer();
        if (!shouldPose(player) || event.getArm() != player.getMainArm()) {
            return;
        }

        int side = event.getArm() == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack poseStack = event.getPoseStack();
        poseStack.translate(side * -0.12F, -0.08F, -0.18F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 8.0F));
    }
}
