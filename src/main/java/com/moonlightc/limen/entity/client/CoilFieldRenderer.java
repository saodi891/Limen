package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.projectile.CoilFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class CoilFieldRenderer extends EntityRenderer<CoilFieldEntity> {
    public CoilFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CoilFieldEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @Override
    public ResourceLocation getTextureLocation(CoilFieldEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png");
    }
}
