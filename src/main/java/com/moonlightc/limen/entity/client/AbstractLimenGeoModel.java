package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

public abstract class AbstractLimenGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final ResourceLocation PLACEHOLDER_MODEL = Limen.id("geo/entity/pama_detector.geo.json");
    private static final ResourceLocation PLACEHOLDER_TEXTURE = Limen.id("textures/entity/pama_detector.png");
    private static final ResourceLocation PLACEHOLDER_ANIMATION = Limen.id("animations/entity/pama_detector.animation.json");

    private final ResourceLocation modelResource;
    private final ResourceLocation textureResource;
    private final ResourceLocation animationResource;

    protected AbstractLimenGeoModel(String modelPath, String texturePath, String animationPath) {
        this.modelResource = modelPath == null ? PLACEHOLDER_MODEL : Limen.id(modelPath);
        this.textureResource = texturePath == null ? PLACEHOLDER_TEXTURE : Limen.id(texturePath);
        this.animationResource = animationPath == null ? PLACEHOLDER_ANIMATION : Limen.id(animationPath);
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return this.modelResource;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return this.textureResource;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return this.animationResource;
    }
}
