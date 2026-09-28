package com.moonlightc.limen.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moonlightc.limen.entity.projectile.PamaTridentEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the in-flight PAMA trident with the shared GeckoLib model, oriented like the vanilla thrown
 * trident: yaw offset by -90° and pitch by +90° so the point leads the flight path.
 */
public class PamaTridentRenderer extends GeoEntityRenderer<PamaTridentEntity> {
    public PamaTridentRenderer(EntityRendererProvider.Context context) {
        super(context, new PamaTridentModel<>());
    }

    @Override
    protected void applyRotations(PamaTridentEntity animatable, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick) {
        float yaw = Mth.lerp(partialTick, animatable.yRotO, animatable.getYRot());
        float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch + 90.0F));
    }
}
