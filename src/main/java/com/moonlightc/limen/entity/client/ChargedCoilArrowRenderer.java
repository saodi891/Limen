package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.projectile.ChargedCoilArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class ChargedCoilArrowRenderer extends ArrowRenderer<ChargedCoilArrowEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("limen", "textures/entity/charged_coil_arrow.png");

    public ChargedCoilArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ChargedCoilArrowEntity entity) {
        return TEXTURE;
    }
}
