package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.PoweredExoskeletonItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

public class PoweredExoskeletonModel extends GeoModel<PoweredExoskeletonItem> {
    @Override
    public ResourceLocation getModelResource(PoweredExoskeletonItem animatable) {
        return Limen.id("geo/armor/powered_exoskeleton.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PoweredExoskeletonItem animatable) {
        return Limen.id("textures/armor/powered_exoskeleton.png");
    }

    @Override
    public ResourceLocation getTextureResource(PoweredExoskeletonItem animatable,
                                              GeoRenderer<PoweredExoskeletonItem> renderer) {
        if (renderer instanceof GeoArmorRenderer<?> armorRenderer
                && PoweredExoskeletonItem.isWornOut(armorRenderer.getCurrentStack())) {
            return Limen.id("textures/armor/powered_exoskeleton_broken.png");
        }
        return getTextureResource(animatable);
    }

    @Override
    public ResourceLocation getAnimationResource(PoweredExoskeletonItem animatable) {
        return Limen.id("animations/armor/powered_exoskeleton.animation.json");
    }
}
