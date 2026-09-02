package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.PamaDetectorEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class PamaDetectorModel extends GeoModel<PamaDetectorEntity> {
    @Override
    public ResourceLocation getModelResource(PamaDetectorEntity animatable) {
        return Limen.id("geo/entity/pama_detector.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PamaDetectorEntity animatable) {
        return Limen.id("textures/entity/pama_detector.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PamaDetectorEntity animatable) {
        return Limen.id("animations/entity/pama_detector.animation.json");
    }

    @Override
    public void setCustomAnimations(PamaDetectorEntity animatable, long instanceId, AnimationState<PamaDetectorEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // Head-only model: apply the vanilla look orientation to the "head" bone so it turns to
        // face what the entity's LookControl is aimed at.
        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head == null) {
            return;
        }

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        head.setRotY(modelData.netHeadYaw() * Mth.DEG_TO_RAD);
        head.setRotX(modelData.headPitch() * Mth.DEG_TO_RAD);
    }
}
