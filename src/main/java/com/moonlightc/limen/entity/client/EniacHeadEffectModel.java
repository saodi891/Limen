package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.EniacEntity;
import net.minecraft.resources.ResourceLocation;

public class EniacHeadEffectModel extends AbstractLimenGeoModel<EniacEntity> {
    public EniacHeadEffectModel() {
        super(
                "geo/entity/eniac_0.geo.json",
                "textures/entity/eniac_0.png",
                "animations/entity/eniac.animation.json"
        );
    }

    @Override
    public ResourceLocation getTextureResource(EniacEntity eniac) {
        return Limen.id("textures/entity/eniac_0/frame_0" + eniac.getHeadTextureFrame() + ".png");
    }
}
