package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.SilkBabyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SilkBabyRenderer extends GeoEntityRenderer<SilkBabyEntity> {
    public SilkBabyRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SilkBabyModel());
        this.shadowRadius = 0.4F;
    }
}
