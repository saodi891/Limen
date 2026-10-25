package com.moonlightc.limen.client;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-player "deploy" factor (0..1) for the braced polearm, written each render frame by
 * {@code PolearmArmPoseMixin} (which runs in {@code HumanoidModel.setupAnim}) and read later the same frame by
 * {@code ItemInHandLayerMixin} (which runs in the layer pass, after setupAnim). 0 = resting hold pose, 1 = fully
 * deployed forward aim; it eases up on brace enter, holds at 1 while braced, and eases back down on exit — so the
 * polearm item can rotate in step with the arm during the fade-in/fade-out. State lives here, not on either
 * mixin, because both model and layer instances are shared across every player, so it must be keyed by entity. A
 * weak map drops entries when the player unloads. Render-thread only; no synchronization needed.
 */
public final class PolearmPoseState {
    private static final Map<LivingEntity, Float> DEPLOY = new WeakHashMap<>();

    private PolearmPoseState() {
    }

    public static void setDeploy(LivingEntity entity, float deploy) {
        DEPLOY.put(entity, deploy);
    }

    public static float getDeploy(LivingEntity entity) {
        Float deploy = DEPLOY.get(entity);
        return deploy == null ? 0.0F : deploy;
    }

    public static void clearDeploy(LivingEntity entity) {
        DEPLOY.remove(entity);
    }
}
