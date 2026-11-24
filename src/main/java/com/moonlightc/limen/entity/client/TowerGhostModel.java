package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.TowerGhostEntity;
import net.minecraft.resources.ResourceLocation;

public class TowerGhostModel extends AbstractLimenGeoModel<TowerGhostEntity> {
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("limen", "textures/entity/tower_wraith_black.png");
    private static final ResourceLocation BLUE_FIRE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("limen", "textures/entity/tower_wraith_black.png");
    private static final ResourceLocation POISON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("limen", "textures/entity/tower_wraith_magenta.png");

    public TowerGhostModel() {
        super(
                "geo/entity/tower_wraith.geo.json",
                "textures/entity/tower_wraith_black.png",
                "animations/entity/tower_wraith.animation.json"
        );
    }

    @Override
    public ResourceLocation getTextureResource(TowerGhostEntity ghost) {
        return switch (ghost.getCastState()) {
            case TowerGhostEntity.CAST_BLUE_FIRE -> BLUE_FIRE_TEXTURE;
            case TowerGhostEntity.CAST_POISON -> POISON_TEXTURE;
            default -> DEFAULT_TEXTURE;
        };
    }
}
