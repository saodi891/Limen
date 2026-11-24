package com.moonlightc.limen.mixin;

import com.moonlightc.limen.event.DeflectionShieldEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(AbstractArrow.class)
public abstract class DeflectedArrowMixin {
    private static final double MAX_TURN_RADIANS_PER_TICK = Math.toRadians(30.0D);

    @Inject(method = "tick", at = @At("HEAD"))
    private void limen$steerDeflectedArrow(CallbackInfo callbackInfo) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!(arrow.level() instanceof ServerLevel level)) {
            return;
        }

        var data = arrow.getPersistentData();
        String targetTag = DeflectionShieldEvents.STEERING_TARGET_TAG;
        if (!data.hasUUID(targetTag)) {
            return;
        }

        Entity entity = level.getEntity(data.getUUID(targetTag));
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            data.remove(targetTag);
            return;
        }

        Vec3 velocity = arrow.getDeltaMovement();
        double speed = velocity.length();
        Vec3 desired = target.getEyePosition().subtract(arrow.position());
        if (speed < 1.0E-6D || desired.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 currentDirection = velocity.scale(1.0D / speed);
        Vec3 desiredDirection = desired.normalize();
        double dot = Math.max(-1.0D, Math.min(1.0D, currentDirection.dot(desiredDirection)));
        double angle = Math.acos(dot);
        if (angle < 1.0E-6D) {
            return;
        }

        double turn = Math.min(angle, MAX_TURN_RADIANS_PER_TICK);
        Vec3 axis = currentDirection.cross(desiredDirection);
        if (axis.lengthSqr() < 1.0E-8D) {
            axis = Math.abs(currentDirection.y) < 0.9D
                    ? currentDirection.cross(new Vec3(0.0D, 1.0D, 0.0D))
                    : currentDirection.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        axis = axis.normalize();

        Vec3 turnedDirection = currentDirection.scale(Math.cos(turn))
                .add(axis.cross(currentDirection).scale(Math.sin(turn)))
                .add(axis.scale(axis.dot(currentDirection) * (1.0D - Math.cos(turn))))
                .normalize();
        arrow.setDeltaMovement(turnedDirection.scale(speed));
        arrow.hasImpulse = true;
    }
}
