package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.PamaDetectorEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

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
}
