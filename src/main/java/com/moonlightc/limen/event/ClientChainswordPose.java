package com.moonlightc.limen.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.ChainswordItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sawing pose for the chainsword. While grinding, the holder's arm is pushed forward and given a
 * high-frequency shake so the weapon reads as a running saw, in both third and first person.
 *
 * <p>Poses are applied in the render pass rather than through an animation library so no extra
 * dependency is needed; the arm rotation is restored afterwards to avoid leaking onto other models.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ClientChainswordPose {
    /** Radians the weapon arm is raised toward the target while sawing. */
    private static final float ARM_PITCH = -1.35F;
    /** Inward angle so the saw is held across the body rather than straight ahead. */
    private static final float ARM_YAW = 0.25F;
    /** Amplitude and speed of the saw vibration. */
    private static final float SHAKE_AMPLITUDE = 0.06F;
    private static final float SHAKE_SPEED = 1.9F;

    private ClientChainswordPose() {
    }

    private static float shake(float ageInTicks, float partialTick) {
        return Mth.sin((ageInTicks + partialTick) * SHAKE_SPEED) * SHAKE_AMPLITUDE;
    }

    private static boolean shouldPose(Player player) {
        return player.getMainHandItem().getItem() instanceof ChainswordItem
                && ClientChainswordEvents.isGrinding(player);
    }

    /** Third person: bend the weapon arm into the sawing pose before the player model is drawn. */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!shouldPose(player)) {
            return;
        }

        PlayerRenderer renderer = event.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        var arm = rightHanded ? model.rightArm : model.leftArm;
        var otherArm = rightHanded ? model.leftArm : model.rightArm;

        float wobble = shake(player.tickCount, event.getPartialTick());
        float inward = rightHanded ? -ARM_YAW : ARM_YAW;

        arm.xRot = ARM_PITCH + wobble;
        arm.yRot = inward;
        arm.zRot = inward * 0.5F;

        // Support arm braces the weapon, which sells the two-handed grind.
        otherArm.xRot = ARM_PITCH * 0.75F + wobble * 0.5F;
        otherArm.yRot = -inward * 1.4F;
        otherArm.zRot = -inward * 0.5F;
    }

    /**
     * First person: shift and rotate the held arm (and with it the item model) so the saw sits in
     * view and vibrates, instead of using the default idle hand position.
     */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        Player player = event.getPlayer();
        if (!shouldPose(player)) {
            return;
        }
        if (event.getArm() != player.getMainArm()) {
            return;
        }

        // RenderArmEvent carries no partial tick, so use the render-time partial tick for a shake
        // that advances smoothly between ticks instead of stepping once per tick.
        float partialTick = net.minecraft.client.Minecraft.getInstance().getPartialTick();
        float wobble = shake(player.tickCount, partialTick);
        boolean rightHanded = event.getArm() == HumanoidArm.RIGHT;
        int side = rightHanded ? 1 : -1;

        PoseStack poseStack = event.getPoseStack();
        // Pull the arm inward and forward so the weapon is presented across the view.
        poseStack.translate(side * -0.18F, -0.12F + wobble * 0.5F, -0.22F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-28.0F + wobble * 40.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 16.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -10.0F));
    }
}
