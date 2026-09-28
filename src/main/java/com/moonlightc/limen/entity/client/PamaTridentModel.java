package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * Shared GeckoLib model for the PAMA trident. The same geometry, texture and (empty) animation are used by
 * both the held/inventory item renderer and the in-flight projectile renderer.
 */
public class PamaTridentModel<T extends GeoAnimatable> extends GeoModel<T> {
    @Override
    public ResourceLocation getModelResource(T animatable) {
        return Limen.id("geo/entity/pama_trident.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return Limen.id("textures/entity/pama_trident.png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return Limen.id("animations/item/pama_trident.animation.json");
    }
}
