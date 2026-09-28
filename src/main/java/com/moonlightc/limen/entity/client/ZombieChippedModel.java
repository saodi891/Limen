package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.ZombieChippedEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;

public class ZombieChippedModel extends AbstractLimenGeoModel<ZombieChippedEntity> {
    public ZombieChippedModel() {
        super(
                "geo/entity/zombie_chipped.geo.json",
                "textures/entity/zombie_chipped.png",
                "animations/entity/zombie_chipped.animation.json"
        );
    }

    @Override
    public void setCustomAnimations(ZombieChippedEntity animatable, long instanceId, AnimationState<ZombieChippedEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        float limbSwing = animationState.getLimbSwing();
        float limbSwingAmount = Math.min(animationState.getLimbSwingAmount(), 1.0F);
        float walkSpeed = 0.6662F;
        float walkAmplitude = 1.4F * limbSwingAmount;

        float headYaw = 0.0F;
        float headPitch = 0.0F;
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entityData != null) {
            headYaw = entityData.netHeadYaw() * Mth.DEG_TO_RAD;
            headPitch = entityData.headPitch() * Mth.DEG_TO_RAD;
        }
        final float finalHeadYaw = headYaw;
        final float finalHeadPitch = headPitch;

        this.getBone("Head").ifPresent(bone -> {
            float baseRotX = bone.getInitialSnapshot().getRotX();
            float baseRotY = bone.getInitialSnapshot().getRotY();
            bone.setRotY(baseRotY + finalHeadYaw);
            bone.setRotX(baseRotX + finalHeadPitch);
        });

        applyLegSwing("RightLeg", limbSwing, 0.0F, walkSpeed, walkAmplitude);
        applyLegSwing("LeftLeg", limbSwing, Mth.PI, walkSpeed, walkAmplitude);

        // Classic zombie arms: both held straight out forward, with a melee swing and idle sway,
        // mirroring vanilla AnimationUtils.animateZombieArms so this reads like a normal zombie.
        float ageInTicks = (float) animationState.getAnimationTick();
        float attackTime = animatable.getAttackAnim(animationState.getPartialTick());
        applyZombieArms(ageInTicks, attackTime);
    }

    private void applyLegSwing(String boneName, float limbSwing, float phaseOffset, float walkSpeed, float walkAmplitude) {
        this.getBone(boneName).ifPresent(bone -> {
            float baseRotX = bone.getInitialSnapshot().getRotX();
            bone.setRotX(baseRotX + Mth.cos(limbSwing * walkSpeed + phaseOffset) * walkAmplitude);
        });
    }

    private void applyZombieArms(float ageInTicks, float attackTime) {
        float f = Mth.sin(attackTime * Mth.PI);
        float f1 = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * Mth.PI);

        // Arms already rest at -90 (straight out) in the geo, so these are deltas on top of that pose,
        // matching vanilla AnimationUtils.animateZombieArms one-for-one.
        float swing = -(f * 1.2F - f1 * 0.4F);          // melee windup + strike
        float idleX = Mth.sin(ageInTicks * 0.067F) * 0.05F;
        float idleZ = Mth.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        float yaw = f * 0.6F;                            // arms splay while striking

        applyArmPose("RightArm", swing + idleX, -(0.1F - yaw), idleZ);
        applyArmPose("LeftArm", swing - idleX, 0.1F - yaw, -idleZ);
    }

    private void applyArmPose(String boneName, float deltaRotX, float deltaRotY, float deltaRotZ) {
        this.getBone(boneName).ifPresent(bone -> {
            bone.setRotX(bone.getInitialSnapshot().getRotX() + deltaRotX);
            bone.setRotY(bone.getInitialSnapshot().getRotY() + deltaRotY);
            bone.setRotZ(bone.getInitialSnapshot().getRotZ() + deltaRotZ);
        });
    }
}
