package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.TowerGuardEntity;

public class TowerGuardModel extends AbstractLimenGeoModel<TowerGuardEntity> {
    public TowerGuardModel() {
        super(
                "geo/entity/tower_guard.geo.json",
                "textures/entity/tower_guard.png",
                "animations/entity/tower_guard.animation.json"
        );
    }
}
