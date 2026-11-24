package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.projectile.TowerGhostBlueFlameEntity;

public class TowerGhostBlueFlameModel extends AbstractLimenGeoModel<TowerGhostBlueFlameEntity> {
    public TowerGhostBlueFlameModel() {
        super(
                "geo/entity/soul_fire.geo.json",
                "textures/entity/soul_fire.png",
                "animations/entity/soul_fire.animation.json"
        );
    }
}
