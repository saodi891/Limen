package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.SkeletonChippedEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;

public class SkeletonChippedModel extends AbstractLimenGeoModel<SkeletonChippedEntity> {
    public SkeletonChippedModel() {
        super(
                "geo/entity/skeleton_chipped.geo.json",
                "textures/entity/skeleton_chipped.png",
                "animations/entity/skeleton_chipped.animation.json"
        );
    }

    @Override
    public void setCustomAnimations(SkeletonChippedEntity animatable, long instanceId, AnimationState<SkeletonChippedEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

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
            bone.setRotY(bone.getInitialSnapshot().getRotY() + finalHeadYaw);
            bone.setRotX(bone.getInitialSnapshot().getRotX() + finalHeadPitch);
        });

        float limbSwing = animationState.getLimbSwing();
        float limbSwingAmount = Math.min(animationState.getLimbSwingAmount(), 1.0F);
        float walkSpeed = 0.6662F;
        float walkAmplitude = 1.4F * limbSwingAmount;

        applyLegSwing("RightLeg", limbSwing, 0.0F, walkSpeed, walkAmplitude);
        applyLegSwing("LeftLeg", limbSwing, Mth.PI, walkSpeed, walkAmplitude);

        boolean bowPose = animatable.getMainHandItem().is(Items.BOW) && animatable.isAggressive();
        if (bowPose) {
            applyBowPose(finalHeadYaw, finalHeadPitch);
        } else {
            float armSwing = Mth.cos(limbSwing * walkSpeed) * 0.8F * limbSwingAmount;
            if (animatable.isAggressive()) {
                armSwing += 0.35F;
            }

            applyArmSwing("RightArm", -armSwing);
            applyArmSwing("LeftArm", armSwing);
        }
    }

    private void applyBowPose(float headYaw, float headPitch) {
        this.getBone("RightArm").ifPresent(bone -> {
            bone.setRotY(bone.getInitialSnapshot().getRotY() - 0.1F + headYaw);
            bone.setRotX(bone.getInitialSnapshot().getRotX() - Mth.HALF_PI + headPitch);
            bone.setRotZ(bone.getInitialSnapshot().getRotZ());
        });

        this.getBone("LeftArm").ifPresent(bone -> {
            bone.setRotY(bone.getInitialSnapshot().getRotY() + 0.5F + headYaw);
            bone.setRotX(bone.getInitialSnapshot().getRotX() - 0.97079635F + headPitch);
            bone.setRotZ(bone.getInitialSnapshot().getRotZ());
        });
    }

    private void applyLegSwing(String boneName, float limbSwing, float phaseOffset, float walkSpeed, float walkAmplitude) {
        this.getBone(boneName).ifPresent(bone -> bone.setRotX(bone.getInitialSnapshot().getRotX() + Mth.cos(limbSwing * walkSpeed + phaseOffset) * walkAmplitude));
    }

    private void applyArmSwing(String boneName, float deltaRotX) {
        this.getBone(boneName).ifPresent(bone -> bone.setRotX(bone.getInitialSnapshot().getRotX() + deltaRotX));
    }
}
