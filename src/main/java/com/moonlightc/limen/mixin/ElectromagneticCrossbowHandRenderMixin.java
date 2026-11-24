package com.moonlightc.limen.mixin;

import com.moonlightc.limen.item.ElectromagneticCrossbowItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ElectromagneticCrossbowHandRenderMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void limen$hideOffhandDuringCrossbowUse(AbstractClientPlayer player, float partialTick, float pitch,
                                                    InteractionHand hand, float swingProgress, ItemStack stack,
                                                    float equippedProgress, PoseStack poseStack,
                                                    MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (hand != InteractionHand.OFF_HAND) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof ElectromagneticCrossbowItem)) {
            return;
        }

        boolean pulling = player.isUsingItem() && player.getUseItem() == mainHand;
        boolean autoReloading = mainHand.getTag() != null
                && mainHand.getTag().contains(ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG);
        if (CrossbowItem.isCharged(mainHand) || pulling || autoReloading) {
            ci.cancel();
        }
    }
}
