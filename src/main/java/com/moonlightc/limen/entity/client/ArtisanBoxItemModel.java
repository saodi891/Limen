package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.ArtisanBoxItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Shares the artisan box geometry and texture with the block, so item and block look identical. */
public class ArtisanBoxItemModel extends GeoModel<ArtisanBoxItem> {
    @Override
    public ResourceLocation getModelResource(ArtisanBoxItem animatable) {
        return Limen.id("geo/block/artisan_box.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ArtisanBoxItem animatable) {
        return Limen.id("textures/block/artisan_box.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ArtisanBoxItem animatable) {
        return Limen.id("animations/block/artisan_box.animation.json");
    }
}
