package com.moonlightc.limen.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moonlightc.limen.entity.monster.EniacEntity;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class EniacRenderer extends GeoEntityRenderer<EniacEntity> {
    public EniacRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new EniacModel());
        this.addRenderLayer(new EniacHeadEffectLayer(this));
        this.addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, EniacEntity eniac) {
                return "itemRight".equals(bone.getName())
                        && eniac.getMainHandItem().is(ModItems.ELECTROMAGNETIC_CROSSBOW.get())
                        ? eniac.getMainHandItem() : ItemStack.EMPTY;
            }

            @Override
            protected BlockState getBlockForBone(GeoBone bone, EniacEntity eniac) {
                return null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(
                    GeoBone bone, ItemStack stack, EniacEntity eniac) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                              EniacEntity eniac,
                                              net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                              float partialTick, int packedLight, int packedOverlay) {
                poseStack.pushPose();
                super.renderStackForBone(poseStack, bone, stack, eniac, bufferSource,
                        partialTick, packedLight, packedOverlay);
                poseStack.popPose();
            }
        });
        this.shadowRadius = 0.5F;
    }
}
