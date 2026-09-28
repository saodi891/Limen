package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.misc.TotemOfRelinkEntity;

public class TotemOfRelinkModel extends AbstractLimenGeoModel<TotemOfRelinkEntity> {
    public TotemOfRelinkModel() {
        super(
                "geo/entity/totem_of_relink.geo.json",
                "textures/entity/totem_of_relink.png",
                "animations/entity/totem_of_relink.animation.json"
        );
    }
}
