package com.moonlightc.limen.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moonlightc.limen.entity.MechanicTrappedState;
import com.moonlightc.limen.entity.client.MechanicTrappedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select a static model without replacing the vanilla renderer or its clothing layers. */
@Mixin(LivingEntityRenderer.class)
public abstract class MechanicTrappedRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Unique
    private MechanicTrappedModel limen$trappedModel;

    @Unique
    private M limen$originalModel;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void limen$createTrappedModel(EntityRendererProvider.Context context, M model, float shadowRadius,
                                           CallbackInfo callback) {
        if ((Object) this instanceof VillagerRenderer) {
            this.limen$trappedModel = new MechanicTrappedModel(context.bakeLayer(MechanicTrappedModel.LAYER_LOCATION));
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void limen$selectTrappedModel(T entity, float yaw, float partialTick, PoseStack poseStack,
                                          MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (this.limen$trappedModel != null && entity instanceof Villager villager
                && MechanicTrappedState.isTrapped(villager)) {
            this.limen$originalModel = this.model;
            this.model = (M) (EntityModel<?>) this.limen$trappedModel;
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void limen$restoreVillagerModel(T entity, float yaw, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (this.limen$originalModel != null) {
            this.model = this.limen$originalModel;
            this.limen$originalModel = null;
        }
    }
}
