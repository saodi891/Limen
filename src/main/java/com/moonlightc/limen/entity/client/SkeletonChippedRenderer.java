package com.moonlightc.limen.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moonlightc.limen.entity.monster.SkeletonChippedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class SkeletonChippedRenderer extends GeoEntityRenderer<SkeletonChippedEntity> {
    // Tune the held-bow angle here. The vanilla bow's thirdperson transform is authored for the vanilla
    // arm, so it needs correcting for this rig. Z stands the bow upright (fixes it lying flat), X tilts
    // it forward/back, Y swings it left/right. Adjust these three until it sits right in the hand.
    private static final float BOW_ROT_X_DEG = 0.0F;
    private static final float BOW_ROT_Y_DEG = 0.0F;
    private static final float BOW_ROT_Z_DEG = 90.0F;

    public SkeletonChippedRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SkeletonChippedModel());
        this.shadowRadius = 0.5F;
        this.addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, SkeletonChippedEntity animatable) {
                return switch (bone.getName()) {
                    case "rightItem" -> animatable.getMainHandItem();
                    case "leftItem" -> animatable.getOffhandItem();
                    default -> ItemStack.EMPTY;
                };
            }

            @Override
            protected BlockState getBlockForBone(GeoBone bone, SkeletonChippedEntity animatable) {
                return null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, SkeletonChippedEntity animatable) {
                return "leftItem".equals(bone.getName()) ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, SkeletonChippedEntity animatable, net.minecraft.client.renderer.MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
                poseStack.pushPose();

                if ("rightItem".equals(bone.getName())) {
                    // Correct the vanilla bow's orientation for this rig, then nudge it into the palm.
                    poseStack.mulPose(Axis.ZP.rotationDegrees(BOW_ROT_Z_DEG));
                    poseStack.mulPose(Axis.YP.rotationDegrees(BOW_ROT_Y_DEG));
                    poseStack.mulPose(Axis.XP.rotationDegrees(BOW_ROT_X_DEG));
                    poseStack.translate(-0.05D, 0.08D, -0.02D);
                    poseStack.scale(0.9F, 0.9F, 0.9F);
                } else if ("leftItem".equals(bone.getName())) {
                    poseStack.translate(0.05D, 0.08D, -0.02D);
                    poseStack.scale(0.9F, 0.9F, 0.9F);
                }

                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
                poseStack.popPose();
            }
        });
    }
}
