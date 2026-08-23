package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.AutonomousSpiderEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;

public class AutonomousSpiderModel extends AbstractLimenGeoModel<AutonomousSpiderEntity> {
    public AutonomousSpiderModel() {
        super(
                "geo/entity/autonomous_spider.geo.json",
                "textures/entity/autonomous_spider.png",
                "animations/entity/autonomous_spider.animation.json"
        );
    }

    @Override
    public void setCustomAnimations(AutonomousSpiderEntity animatable, long instanceId, AnimationState<AutonomousSpiderEntity> animationState) {
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
        this.getBone("head").ifPresent(bone -> {
            bone.setRotY(bone.getInitialSnapshot().getRotY() + finalHeadYaw);
            bone.setRotX(bone.getInitialSnapshot().getRotX() + finalHeadPitch);
        });

        float limbSwing = animationState.getLimbSwing();
        float limbSwingAmount = Math.min(animationState.getLimbSwingAmount(), 1.0F);
        float walkSpeed = limbSwing * 0.6662F;

        float yawWave0 = -(Mth.cos(walkSpeed * 2.0F) * 0.4F) * limbSwingAmount;
        float yawWave1 = -(Mth.cos(walkSpeed * 2.0F + Mth.PI) * 0.4F) * limbSwingAmount;
        float yawWave2 = -(Mth.cos(walkSpeed * 2.0F + (Mth.HALF_PI)) * 0.4F) * limbSwingAmount;
        float yawWave3 = -(Mth.cos(walkSpeed * 2.0F + (Mth.PI * 1.5F)) * 0.4F) * limbSwingAmount;

        float rollWave0 = Math.abs(Mth.sin(walkSpeed) * 0.4F) * limbSwingAmount;
        float rollWave1 = Math.abs(Mth.sin(walkSpeed + Mth.PI) * 0.4F) * limbSwingAmount;
        float rollWave2 = Math.abs(Mth.sin(walkSpeed + Mth.HALF_PI) * 0.4F) * limbSwingAmount;
        float rollWave3 = Math.abs(Mth.sin(walkSpeed + (Mth.PI * 1.5F)) * 0.4F) * limbSwingAmount;

        applyLegPose("leg0", yawWave0, -rollWave0);
        applyLegPose("leg1", -yawWave0, rollWave0);
        applyLegPose("leg2", yawWave1, -rollWave1);
        applyLegPose("leg3", -yawWave1, rollWave1);
        applyLegPose("leg4", yawWave2, -rollWave2);
        applyLegPose("leg5", -yawWave2, rollWave2);
        applyLegPose("leg6", yawWave3, -rollWave3);
        applyLegPose("leg7", -yawWave3, rollWave3);
    }

    private void applyLegPose(String boneName, float deltaYaw, float deltaRoll) {
        this.getBone(boneName).ifPresent(bone -> {
            bone.setRotY(bone.getInitialSnapshot().getRotY() + deltaYaw);
            bone.setRotZ(bone.getInitialSnapshot().getRotZ() + deltaRoll);
        });
    }
}
