package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.PamaEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public final class PamaModel extends GeoModel<PamaEntity> {
    private static final float MODEL_ORIGIN_X = 248.0F;
    private static final float MODEL_ORIGIN_Y = 448.0F;
    private static final float MODEL_ORIGIN_Z = -8.0F;

    @Override
    public ResourceLocation getModelResource(PamaEntity pama) {
        return Limen.id("geo/entity/pama_0.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PamaEntity pama) {
        return Limen.id("textures/entity/pama/pama_default_0.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PamaEntity pama) {
        return Limen.id("animations/entity/pama_0.animation.json");
    }

    @Override
    public void setCustomAnimations(PamaEntity pama, long instanceId, AnimationState<PamaEntity> animationState) {
        super.setCustomAnimations(pama, instanceId, animationState);
        this.getBone("obj_screenBadlandsTown_default").ifPresent(root -> {
            root.setPosX(MODEL_ORIGIN_X);
            root.setPosY(MODEL_ORIGIN_Y);
            root.setPosZ(MODEL_ORIGIN_Z);
        });
    }
}
