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

        applyLegSwing("RightLeg", limbSwing, walkSpeed, walkAmplitude);
        applyLegSwing("LeftLeg", limbSwing + Mth.PI, walkSpeed, walkAmplitude);

        float armSwing = Mth.cos(limbSwing * walkSpeed) * 0.35F * limbSwingAmount;
        float attackLift = animatable.isAggressive() ? 0.35F : 0.0F;

        applyArmSwing("RightArm", -armSwing - attackLift);
        applyArmSwing("LeftArm", armSwing - attackLift);
    }

    private void applyLegSwing(String boneName, float phase, float walkSpeed, float walkAmplitude) {
        this.getBone(boneName).ifPresent(bone -> {
            float baseRotX = bone.getInitialSnapshot().getRotX();
            bone.setRotX(baseRotX + Mth.cos(phase * walkSpeed) * walkAmplitude);
        });
    }

    private void applyArmSwing(String boneName, float deltaRotX) {
        this.getBone(boneName).ifPresent(bone -> {
            float baseRotX = bone.getInitialSnapshot().getRotX();
            float baseRotY = bone.getInitialSnapshot().getRotY();
            float baseRotZ = bone.getInitialSnapshot().getRotZ();
            bone.setRotX(baseRotX + deltaRotX);
            bone.setRotY(baseRotY);
            bone.setRotZ(baseRotZ);
        });
    }
}
