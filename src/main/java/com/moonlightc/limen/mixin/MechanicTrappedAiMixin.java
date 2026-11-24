package com.moonlightc.limen.mixin;

import com.moonlightc.limen.entity.MechanicTrappedState;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MechanicTrappedAiMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void limen$pauseTrappedMechanicAi(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (MechanicTrappedState.isTrapped(mob)) {
            MechanicTrappedState.stopAutonomousMovement(mob);
            callback.cancel();
        }
    }
}
