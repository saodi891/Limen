package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.item.PoweredExoskeletonItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class PoweredExoskeletonArmorRenderer extends GeoArmorRenderer<PoweredExoskeletonItem> {
    public PoweredExoskeletonArmorRenderer() {
        super(new PoweredExoskeletonModel());
    }

    @Override
    protected void grabRelevantBones(BakedGeoModel model) {
        this.body = model.getBone("Body").orElse(null);
        this.rightLeg = model.getBone("Right Leg").orElse(null);
        this.leftLeg = model.getBone("Left Leg").orElse(null);
    }
}
