package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.PamaDetectorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PamaDetectorRenderer extends GeoEntityRenderer<PamaDetectorEntity> {
    public PamaDetectorRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new PamaDetectorModel());
        this.shadowRadius = 0.35F;
    }
}
