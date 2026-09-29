package com.moonlightc.limen.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moonlightc.limen.entity.monster.SkeletonChippedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class SkeletonChippedRenderer extends GeoEntityRenderer<SkeletonChippedEntity> {
    // Held-bow orientation, applied X->Y->Z on top of the THIRD_PERSON_RIGHT_HAND display transform.
    // rightItem is a child of RightArm, so the arm's own rotation is baked in before these apply: the arm
    // hangs straight down when idle but pitches ~90 degrees forward when aiming (see SkeletonChippedModel
    // #applyBowPose). One fixed triple therefore can't sit upright in both poses, so idle and aim are tuned
    // separately; the aim triple is the idle triple plus ~90 degrees on X to cancel the arm's forward pitch.
    // Tuning guide: Z rolls the bow upright in view, X pitches it forward/back, Y yaws it left/right.
    private static final float BOW_IDLE_ROT_X = 0.0F;
    private static final float BOW_IDLE_ROT_Y = 0.0F;
    private static final float BOW_IDLE_ROT_Z = 90.0F;
    private static final float BOW_AIM_ROT_X = 90.0F;
    private static final float BOW_AIM_ROT_Y = 0.0F;
    private static final float BOW_AIM_ROT_Z = 90.0F;

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

                if ("rightItem".equals(bone.getName()) && stack.is(Items.BOW)) {
                    // Aiming vs idle need different corrections because the arm bone pitches forward when aiming.
                    boolean aiming = animatable.isAggressive();
                    poseStack.mulPose(Axis.XP.rotationDegrees(aiming ? BOW_AIM_ROT_X : BOW_IDLE_ROT_X));
                    poseStack.mulPose(Axis.YP.rotationDegrees(aiming ? BOW_AIM_ROT_Y : BOW_IDLE_ROT_Y));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(aiming ? BOW_AIM_ROT_Z : BOW_IDLE_ROT_Z));
                    poseStack.translate(-0.05D, 0.08D, -0.02D);
                    poseStack.scale(0.9F, 0.9F, 0.9F);
                } else if ("rightItem".equals(bone.getName())) {
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
