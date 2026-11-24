package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.TowerGhostEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TowerGhostRenderer extends GeoEntityRenderer<TowerGhostEntity> {
    public TowerGhostRenderer(EntityRendererProvider.Context context) {
        super(context, new TowerGhostModel());
        this.shadowRadius = 0.45F;
    }
}
