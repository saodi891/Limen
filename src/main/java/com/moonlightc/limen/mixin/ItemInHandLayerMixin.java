package com.moonlightc.limen.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moonlightc.limen.client.PolearmPoseState;
import com.moonlightc.limen.item.PoweredPolearmItem;
import com.moonlightc.limen.item.RoyalPolearmItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates the held polearm item (third person) in step with the arm's deploy animation, so the item itself —
 * not just the arm — swings into the forward aim during the brace fade-in and swings back on the fade-out. The
 * polearm model sits on a 45° diagonal, so without this the tip never lines up with the aim. The deploy factor
 * (0 = rest, 1 = fully forward) is published per-player by {@code PolearmArmPoseMixin} during
 * {@code HumanoidModel.setupAnim}, which runs before this layer, and read here via {@link PolearmPoseState}.
 *
 * <p>Injected just before {@code ItemInHandRenderer.renderItem}: by that point {@code renderArmWithItem} has
 * already called {@code translateToHand} and placed the pose at the hand mount, so {@code mulPose} here rotates
 * the item about the hand (the "hand mount point") without moving the arm, which was already rendered. Guarded
 * to the local polearm so a stale deploy value from a mid-fade item swap cannot rotate a different item.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    /** Degrees of pitch (about the hand's X axis) applied at full deploy to stand the diagonal polearm forward. */
    private static final float LIMEN_DEPLOY_PITCH = -45.0F;
    /** Degrees of roll (about the hand's Z axis) applied at full deploy to true up the diagonal. Tune in-game. */
    private static final float LIMEN_DEPLOY_ROLL = 0.0F;

    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void limen$rotatePolearmInHand(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                           HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int light,
                                           CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (!(stack.getItem() instanceof RoyalPolearmItem || stack.getItem() instanceof PoweredPolearmItem)) {
            return;
        }
        float deploy = PolearmPoseState.getDeploy(player);
        if (deploy <= 0.0F) {
            return;
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(deploy * LIMEN_DEPLOY_PITCH));
        if (LIMEN_DEPLOY_ROLL != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(deploy * LIMEN_DEPLOY_ROLL));
        }
    }
}
