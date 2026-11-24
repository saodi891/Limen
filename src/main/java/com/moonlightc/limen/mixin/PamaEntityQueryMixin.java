package com.moonlightc.limen.mixin;

import com.moonlightc.limen.entity.monster.PamaEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public abstract class PamaEntityQueryMixin {
    @Inject(
            method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private void limen$includeLargePamaInEntityQueries(
            Entity except,
            AABB bounds,
            Predicate<? super Entity> predicate,
            CallbackInfoReturnable<List<Entity>> callback
    ) {
        List<Entity> result = callback.getReturnValue();
        for (PamaEntity pama : PamaEntity.loadedIn((Level) (Object) this)) {
            if (pama != except && pama.isAlive() && bounds.intersects(pama.getBoundingBox())
                    && predicate.test(pama) && !result.contains(pama)) {
                result.add(pama);
            }
        }
    }

    @Inject(
            method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private <T extends Entity> void limen$includeLargePamaInTypedQueries(
            EntityTypeTest<Entity, T> typeTest,
            AABB bounds,
            Predicate<? super T> predicate,
            CallbackInfoReturnable<List<T>> callback
    ) {
        List<T> result = callback.getReturnValue();
        for (PamaEntity pama : PamaEntity.loadedIn((Level) (Object) this)) {
            T typedPama = typeTest.tryCast(pama);
            if (typedPama != null && pama.isAlive() && bounds.intersects(pama.getBoundingBox())
                    && predicate.test(typedPama) && !result.contains(typedPama)) {
                result.add(typedPama);
            }
        }
    }
}
