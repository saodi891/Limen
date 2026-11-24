package com.moonlightc.limen.mixin;

import com.moonlightc.limen.item.ElectromagneticCrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class ElectromagneticCrossbowMixin {
    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void limen$setElectromagneticChargeDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack.getItem() instanceof ElectromagneticCrossbowItem) {
            cir.setReturnValue(20);
        }
    }
}
