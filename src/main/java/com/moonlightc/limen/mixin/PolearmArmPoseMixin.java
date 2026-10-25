package com.moonlightc.limen.mixin;

import com.moonlightc.limen.client.PolearmPoseState;
import com.moonlightc.limen.item.PoweredPolearmItem;
import com.moonlightc.limen.item.RoyalPolearmItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Third-person "held forward" aim for the royal / powered polearm. Targets {@link HumanoidModel} (not
 * {@code PlayerModel}) because {@code rightArm}/{@code leftArm} are declared on {@code HumanoidModel}: a
 * {@code @Shadow} of an inherited field fails to remap in production (the refmap keeps the literal name and
 * mixin can't locate it in {@code PlayerModel}), which crashes on load. {@code PlayerModel#setupAnim} invokes
 * {@code super.setupAnim} first and only copies the sleeves afterward, so injecting at this TAIL still lands
 * after all vanilla arm-rotation work and the sleeves follow the aim. It must run here rather than in a
 * {@code RenderPlayerEvent.Pre} handler, which runs before setupAnim and gets overwritten by the per-frame
 * arm reset. {@code headPitch} tilts the arm up/down and {@code netHeadYaw} (head turn relative to the body)
 * swings it left/right, so the polearm points where the player looks. Only the main hand is posed, matching
 * the one-handed spear grip; non-player humanoids are ignored by the entity check.
 */
@Mixin(HumanoidModel.class)
public abstract class PolearmArmPoseMixin {
    /** Radians to swing a hanging arm up to horizontal; the view pitch is added so the point tracks the head. */
    private static final float LIMEN_ARM_FORWARD = (float) (-Math.PI / 2.0D);
    /** Base inward yaw of the aiming arm, matching vanilla's bow pose, before the head-turn offset is added. */
    private static final float LIMEN_ARM_BASE_YAW = 0.1F;
    private static final float LIMEN_DEG_TO_RAD = (float) Math.PI / 180.0F;
    /** Ticks over which the aim pose eases in from the vanilla hold pose, so the arm does not snap on right-click. */
    private static final float LIMEN_ENTER_TICKS = 4.0F;
    /** Ticks over which the aim pose eases back out after use stops, so the arm does not snap back to idle. */
    private static final float LIMEN_EXIT_TICKS = 4.0F;
    /** Peak forward reach of the left-click jab, as a fraction of the full held-forward swing. */
    private static final float LIMEN_JAB_REACH = 0.9F;

    /**
     * Per-player aim pose captured each render frame while bracing, so the exit fade can keep applying it after
     * {@code isUsingItem()} turns false (at which point this mixin no longer has an aim to compute and vanilla
     * resets the arm). {@code float[]} = {xRot, yRot, zRot, lastAimAgeInTicks}. The model instance is shared
     * across every player of a type, so this state must be keyed by the entity, not stored on the model. A weak
     * map lets entries fall out when the player is unloaded; completed fades remove their own entry.
     */
    private static final Map<LivingEntity, float[]> LIMEN_EXIT_POSE = new WeakHashMap<>();

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void limen$polearmChargeAim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                        float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }

        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart arm = rightHanded ? this.rightArm : this.leftArm;

        if (limen$isBracing(player)) {
            float targetXRot = LIMEN_ARM_FORWARD + headPitch * LIMEN_DEG_TO_RAD;
            float targetYRot = (rightHanded ? -LIMEN_ARM_BASE_YAW : LIMEN_ARM_BASE_YAW) + netHeadYaw * LIMEN_DEG_TO_RAD;
            float targetZRot = 0.0F;

            float blend = limen$ease(Mth.clamp(player.getTicksUsingItem() / LIMEN_ENTER_TICKS, 0.0F, 1.0F));
            arm.xRot = Mth.lerp(blend, arm.xRot, targetXRot);
            arm.yRot = Mth.lerp(blend, arm.yRot, targetYRot);
            arm.zRot = Mth.lerp(blend, arm.zRot, targetZRot);
            LIMEN_EXIT_POSE.put(player, new float[]{arm.xRot, arm.yRot, arm.zRot, ageInTicks});
            PolearmPoseState.setDeploy(player, blend);
            return;
        }

        // Left-click jab: a short forward thrust driven by the swing clock. Overrides vanilla's overhead chop so
        // the stab reads as a point-forward jab, not a sword sweep. Only fires mid-swing with a polearm in the
        // main hand; a swing cancels any lingering exit fade. The swing progress comes from the entity itself
        // (getAttackAnim) rather than a shadowed model field: HumanoidModel has no mappable attackTime field, and
        // ageInTicks is tickCount + partialTick in the renderer, so partialTick = ageInTicks - tickCount.
        float swing = player.getAttackAnim(ageInTicks - entity.tickCount);
        if (swing > 0.0F && limen$isPolearmMain(player)) {
            LIMEN_EXIT_POSE.remove(player);
            float reach = Mth.sin(swing * (float) Math.PI) * LIMEN_JAB_REACH;
            float jabXRot = LIMEN_ARM_FORWARD + headPitch * LIMEN_DEG_TO_RAD;
            float jabYRot = (rightHanded ? -LIMEN_ARM_BASE_YAW : LIMEN_ARM_BASE_YAW) + netHeadYaw * LIMEN_DEG_TO_RAD;
            arm.xRot = Mth.lerp(reach, arm.xRot, jabXRot);
            arm.yRot = Mth.lerp(reach, arm.yRot, jabYRot);
            arm.zRot = Mth.lerp(reach, arm.zRot, 0.0F);
            PolearmPoseState.setDeploy(player, reach);
            return;
        }

        float[] last = LIMEN_EXIT_POSE.get(player);
        if (last == null) {
            PolearmPoseState.clearDeploy(player);
            return;
        }
        float fade = (ageInTicks - last[3]) / LIMEN_EXIT_TICKS;
        if (fade >= 1.0F || fade < 0.0F) {
            LIMEN_EXIT_POSE.remove(player);
            PolearmPoseState.clearDeploy(player);
            return;
        }
        float hold = 1.0F - limen$ease(fade);
        arm.xRot = Mth.lerp(hold, arm.xRot, last[0]);
        arm.yRot = Mth.lerp(hold, arm.yRot, last[1]);
        arm.zRot = Mth.lerp(hold, arm.zRot, last[2]);
        PolearmPoseState.setDeploy(player, hold);
    }

    private static boolean limen$isBracing(Player player) {
        if (!player.isUsingItem()) {
            return false;
        }
        ItemStack use = player.getUseItem();
        return use.getItem() instanceof RoyalPolearmItem || use.getItem() instanceof PoweredPolearmItem;
    }

    private static boolean limen$isPolearmMain(Player player) {
        ItemStack main = player.getMainHandItem();
        return main.getItem() instanceof RoyalPolearmItem || main.getItem() instanceof PoweredPolearmItem;
    }

    /** Smoothstep easing (ease-in-out) for a 0..1 input. */
    private static float limen$ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
