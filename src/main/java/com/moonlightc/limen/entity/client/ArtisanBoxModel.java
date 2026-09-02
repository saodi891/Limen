package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ArtisanBoxModel extends GeoModel<ArtisanBoxBlockEntity> {
    @Override
    public ResourceLocation getModelResource(ArtisanBoxBlockEntity animatable) {
        return Limen.id("geo/block/artisan_box.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ArtisanBoxBlockEntity animatable) {
        return Limen.id("textures/block/artisan_box.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ArtisanBoxBlockEntity animatable) {
        return Limen.id("animations/block/artisan_box.animation.json");
    }
}
