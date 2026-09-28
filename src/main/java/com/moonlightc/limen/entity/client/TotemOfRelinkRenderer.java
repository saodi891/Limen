package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.misc.TotemOfRelinkEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TotemOfRelinkRenderer extends GeoEntityRenderer<TotemOfRelinkEntity> {
    public TotemOfRelinkRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TotemOfRelinkModel());
        this.shadowRadius = 0.3F;
    }
}
