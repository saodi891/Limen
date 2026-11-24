package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.projectile.TowerGhostBlueFlameEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TowerGhostBlueFlameRenderer extends GeoEntityRenderer<TowerGhostBlueFlameEntity> {
    public TowerGhostBlueFlameRenderer(EntityRendererProvider.Context context) {
        super(context, new TowerGhostBlueFlameModel());
        this.shadowRadius = 0.0F;
    }
}
