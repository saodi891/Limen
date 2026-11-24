package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.PamaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class PamaRenderer extends GeoEntityRenderer<PamaEntity> {
    public PamaRenderer(EntityRendererProvider.Context context) {
        super(context, new PamaModel());
        this.shadowRadius = 0.0F;
    }
}
