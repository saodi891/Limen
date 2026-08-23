package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.AutonomousSpiderEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AutonomousSpiderRenderer extends GeoEntityRenderer<AutonomousSpiderEntity> {
    public AutonomousSpiderRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new AutonomousSpiderModel());
        this.shadowRadius = 0.8F;
    }
}
