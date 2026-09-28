package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.SilkBabyEntity;

public class SilkBabyModel extends AbstractLimenGeoModel<SilkBabyEntity> {
    public SilkBabyModel() {
        super(
                "geo/entity/silk_baby.geo.json",
                "textures/entity/silk_baby.png",
                "animations/entity/silk_baby.animation.json"
        );
    }
}
