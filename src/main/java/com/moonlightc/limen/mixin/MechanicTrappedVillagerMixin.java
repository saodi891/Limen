package com.moonlightc.limen.mixin;

import com.moonlightc.limen.entity.MechanicTrappedState;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class MechanicTrappedVillagerMixin {
    @Inject(method = "canBreed", at = @At("HEAD"), cancellable = true)
    private void limen$preventTrappedBreeding(CallbackInfoReturnable<Boolean> callback) {
        if (MechanicTrappedState.isTrapped((Villager) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void limen$holdTrappedFacing(CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        if (MechanicTrappedState.isTrapped(villager)) {
            MechanicTrappedState.holdFacing(villager);
        }
    }
}
