package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.ZombieChippedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ZombieChippedRenderer extends GeoEntityRenderer<ZombieChippedEntity> {
    public ZombieChippedRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ZombieChippedModel());
        this.shadowRadius = 0.5F;
    }
}
