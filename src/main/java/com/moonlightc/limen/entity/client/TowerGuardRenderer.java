package com.moonlightc.limen.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moonlightc.limen.entity.monster.TowerGuardEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class TowerGuardRenderer extends GeoEntityRenderer<TowerGuardEntity> {
    public TowerGuardRenderer(EntityRendererProvider.Context context) {
        super(context, new TowerGuardModel());
        this.shadowRadius = 0.5F;
        this.addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, TowerGuardEntity guard) {
                return "rightItem".equals(bone.getName()) ? guard.getMainHandItem() : ItemStack.EMPTY;
            }

            @Override
            protected BlockState getBlockForBone(GeoBone bone, TowerGuardEntity guard) {
                return null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, TowerGuardEntity guard) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                              TowerGuardEntity guard,
                                              net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                              float partialTick, int packedLight, int packedOverlay) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                poseStack.translate(0.0D, 0.05D, -0.08D);
                poseStack.scale(0.9F, 0.9F, 0.9F);
                super.renderStackForBone(poseStack, bone, stack, guard, bufferSource,
                        partialTick, packedLight, packedOverlay);
                poseStack.popPose();
            }
        });
    }
}
