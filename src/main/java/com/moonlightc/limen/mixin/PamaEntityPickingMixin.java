package com.moonlightc.limen.mixin;

import com.moonlightc.limen.entity.monster.PamaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(GameRenderer.class)
public abstract class PamaEntityPickingMixin {
    @Inject(method = "pick", at = @At("TAIL"))
    private void limen$pickWholePama(float partialTick, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = minecraft.getCameraEntity();
        if (minecraft.level == null || minecraft.player == null || cameraEntity == null) {
            return;
        }

        Vec3 start = cameraEntity.getEyePosition(partialTick);
        Vec3 view = cameraEntity.getViewVector(partialTick);
        double reach = Math.max(minecraft.gameMode.getPickRange(), minecraft.player.getEntityReach());
        Vec3 end = start.add(view.scale(reach));
        AABB searchBounds = new AABB(start, end).inflate(1.0D);

        PamaEntity closestPama = null;
        Vec3 closestHit = null;
        double closestDistance = Double.MAX_VALUE;
        // Oversized entities are indexed by their center section, so a ray near an outer edge
        // may not find PAMA through a local section query.
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof PamaEntity pama) || !searchBounds.intersects(pama.getBoundingBox())) {
                continue;
            }
            if (!pama.isAlive() || !pama.canBeCollidedWith()) {
                continue;
            }

            Optional<Vec3> intersection = pama.getBoundingBox()
                    .inflate(pama.getPickRadius())
                    .clip(start, end);
            if (intersection.isEmpty()) {
                continue;
            }

            double distance = start.distanceToSqr(intersection.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPama = pama;
                closestHit = intersection.get();
            }
        }

        if (closestPama == null) {
            return;
        }

        HitResult currentHit = minecraft.hitResult;
        if (currentHit != null && currentHit.getType() != HitResult.Type.MISS) {
            double currentDistance = start.distanceToSqr(currentHit.getLocation());
            if (currentHit.getType() == HitResult.Type.BLOCK && currentDistance <= closestDistance) {
                return;
            }
            if (currentHit.getType() == HitResult.Type.ENTITY && currentDistance <= closestDistance) {
                return;
            }
        }

        minecraft.crosshairPickEntity = closestPama;
        minecraft.hitResult = new EntityHitResult(closestPama, closestHit);
    }
}
