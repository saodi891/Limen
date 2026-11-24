package com.moonlightc.limen.mixin;

import com.moonlightc.limen.item.PoweredPolearmItem;
import com.moonlightc.limen.item.PoweredExoskeletonItem;
import com.moonlightc.limen.item.RoyalPolearmItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While an item is in use {@code LocalPlayer.aiStep()} multiplies the movement input
 * {@code Input.leftImpulse}/{@code Input.forwardImpulse} by {@code 0.2F} (two {@code ldc 0.2f} inside the
 * {@code isUsingItem()} block), crippling horizontal walking to ~20% speed. The polearm charge scales damage
 * with real movement speed, so a braced polearm never reached the charge threshold horizontally — only a
 * gravity dive from directly above worked. We cancel that multiplier (return {@code 1.0F}) only for the local
 * player bracing a polearm, restoring NORMAL walk speed. Unlike the old MOVEMENT_SPEED modifier this never
 * touches an attribute, so {@code AbstractClientPlayer#getFieldOfViewModifier} (which reads MOVEMENT_SPEED)
 * leaves the FOV untouched and the player's speed is normal rather than boosted.
 *
 * <p>Must target {@code LocalPlayer} (client-only mixin), NOT {@code LivingEntity}: the slowdown lives in
 * {@code LocalPlayer.aiStep}, not {@code LivingEntity.aiStep}, so a mixin on {@code LivingEntity.aiStep} finds
 * the method but zero {@code 0.2F} constants and fails injection on load. Client-only is also sufficient: the
 * walk speed is driven by the client input and the server moves the player to the client-reported position, so
 * restoring the input here makes the real position delta (which the server-side charge reads) grow correctly.
 *
 * <p>Uses plain Mixin {@code @ModifyConstant} rather than MixinExtras: MixinExtras is bundled by Forge only at
 * runtime, not on the compile classpath, and the sponge annotation processor does not emit a refmap entry for
 * MixinExtras annotations. {@code @ModifyConstant} is handled natively by that AP. The guard returns {@code 1.0F}
 * while bracing a polearm or using an item whose slowdown the powered exoskeleton cancels.
 */
@Mixin(LocalPlayer.class)
public abstract class PolearmBraceSlowdownMixin {
    @Unique
    private boolean limen$keepSprintDuringGearUse;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void limen$captureSprint(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        this.limen$keepSprintDuringGearUse = player.isSprinting()
                && PoweredExoskeletonItem.shouldIgnoreUseSlowdown(player, player.getUseItem());
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void limen$restoreSprint(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (this.limen$keepSprintDuringGearUse
                && PoweredExoskeletonItem.shouldIgnoreUseSlowdown(player, player.getUseItem())
                && !player.isCrouching()
                && player.input.forwardImpulse > 0.0F) {
            player.setSprinting(true);
        }
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(floatValue = 0.2F))
    private float limen$cancelBraceSlowdown(float original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!player.isUsingItem()) {
            return original;
        }
        ItemStack use = player.getUseItem();
        if (PoweredExoskeletonItem.shouldIgnoreUseSlowdown(player, use)) {
            return 1.0F;
        }
        if (use.getItem() instanceof RoyalPolearmItem || use.getItem() instanceof PoweredPolearmItem) {
            return 1.0F;
        }
        return original;
    }
}
