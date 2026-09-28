package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.EniacEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EniacRenderer extends GeoEntityRenderer<EniacEntity> {
    public EniacRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EniacModel());
        this.shadowRadius = 0.5F;
    }
}
