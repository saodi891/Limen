package com.moonlightc.limen.entity.client;

import com.moonlightc.limen.entity.monster.EniacEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class EniacHeadEffectLayer extends GeoRenderLayer<EniacEntity> {
    private static final String[] POSE_BONES = {"all", "Waist", "Head"};
    private final EniacHeadEffectModel headModel = new EniacHeadEffectModel();

    public EniacHeadEffectLayer(GeoRenderer<EniacEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            EniacEntity eniac,
            BakedGeoModel bakedModel,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer vertexConsumer,
            float partialTick,
            int packedLight,
            int packedOverlay
    ) {
        if (eniac.getPhase() != 2) {
            return;
        }

        BakedGeoModel headBakedModel = headModel.getBakedModel(headModel.getModelResource(eniac));
        copyPose(bakedModel, headBakedModel);

        var headTexture = headModel.getTextureResource(eniac);
        RenderType headRenderType = headModel.getRenderType(eniac, headTexture);
        VertexConsumer headConsumer = bufferSource.getBuffer(headRenderType);
        for (GeoBone rootBone : headBakedModel.topLevelBones()) {
            renderer.renderRecursively(
                    poseStack,
                    eniac,
                    rootBone,
                    headRenderType,
                    bufferSource,
                    headConsumer,
                    false,
                    partialTick,
                    packedLight,
                    packedOverlay,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
        }
    }

    private void copyPose(BakedGeoModel source, BakedGeoModel target) {
        for (String boneName : POSE_BONES) {
            source.getBone(boneName).ifPresent(sourceBone ->
                    target.getBone(boneName).ifPresent(targetBone -> {
                        targetBone.setPosX(sourceBone.getPosX());
                        targetBone.setPosY(sourceBone.getPosY());
                        targetBone.setPosZ(sourceBone.getPosZ());
                        targetBone.setRotX(sourceBone.getRotX());
                        targetBone.setRotY(sourceBone.getRotY());
                        targetBone.setRotZ(sourceBone.getRotZ());
                        targetBone.setScaleX(sourceBone.getScaleX());
                        targetBone.setScaleY(sourceBone.getScaleY());
                        targetBone.setScaleZ(sourceBone.getScaleZ());
                        targetBone.setHidden(false);
                        targetBone.setChildrenHidden(false);
                    })
            );
        }
    }
}
