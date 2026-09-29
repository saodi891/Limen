package com.moonlightc.limen.mixin;

import com.moonlightc.limen.client.CampfireCameraController;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repositions the camera to an isometric-leaning overhead shot of the campfire while the marshmallow
 * roasting screen is open. Runs at the tail of {@link Camera#setup}, so it overrides whatever pose
 * vanilla just computed, and blends from the player's own view to the overhead view using the pan
 * progress owned by {@link CampfireCameraController}.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    /** Half-size of the box swept during terrain clipping (matches vanilla's 0.1 corner sampling). */
    private static final double CAM_CLIP_BOX = 0.1D;
    /** Blocks nearest the campfire skipped when clipping, so the campfire's own block never counts as a hit. */
    private static final double CAM_CLIP_SELF_CLEAR = 1.0D;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract float getYRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void limen$campfireOverhead(BlockGetter area, Entity entity, boolean detached, boolean reverse,
                                        float partialTicks, CallbackInfo ci) {
        float t = CampfireCameraController.getEasedProgress(partialTicks);
        if (t <= 0.0F) {
            return;
        }
        BlockPos pos = CampfireCameraController.getCampfirePos();
        if (pos == null) {
            return;
        }

        Vec3 fromPos = this.getPosition();
        float fromYaw = this.getYRot();
        float fromPitch = this.getXRot();

        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        float yaw = CampfireCameraController.CAM_YAW;
        float pitch = CampfireCameraController.CAM_PITCH;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        double length = CampfireCameraController.CAM_HEIGHT / Math.sin(pitchRad);
        double toX = cx - fx * length;
        double toY = cy - fy * length;
        double toZ = cz - fz * length;

        float lerpYaw = Mth.rotLerp(t, fromYaw, yaw);
        float lerpPitch = Mth.lerp(t, fromPitch, pitch);
        this.setRotation(lerpYaw, lerpPitch);

        double px = Mth.lerp((double) t, fromPos.x, toX);
        double py = Mth.lerp((double) t, fromPos.y, toY);
        double pz = Mth.lerp((double) t, fromPos.z, toZ);
        // Keep the camera out of terrain: pull it in along the campfire->camera sightline to just before
        // the first block, exactly like vanilla third-person collision. Anchoring the ray at the campfire
        // means the shot stays centred on the scene, only closer, when the ceiling or walls are tight.
        Vec3 clamped = limen$clampToTerrain(area, new Vec3(cx, cy, cz), new Vec3(px, py, pz));
        this.setPosition(clamped.x, clamped.y, clamped.z);
    }

    /**
     * Mirrors {@code Camera#getMaxZoom}: samples eight corners of a small box swept from the anchor
     * (campfire) toward the desired camera position and clamps the distance to the nearest block hit, so
     * the near clip plane never pokes through terrain. The first {@value #CAM_CLIP_SELF_CLEAR} blocks are
     * skipped so the campfire's own block cannot register as a hit and yank the camera onto the fire.
     */
    private Vec3 limen$clampToTerrain(BlockGetter area, Vec3 anchor, Vec3 desired) {
        Vec3 delta = desired.subtract(anchor);
        double dist = delta.length();
        if (dist <= CAM_CLIP_SELF_CLEAR) {
            return desired;
        }
        Vec3 dir = delta.scale(1.0D / dist);
        Vec3 rayStart = anchor.add(dir.scale(CAM_CLIP_SELF_CLEAR));
        double maxDist = dist;
        for (int i = 0; i < 8; ++i) {
            double ox = ((i & 1) * 2 - 1) * CAM_CLIP_BOX;
            double oy = ((i >> 1 & 1) * 2 - 1) * CAM_CLIP_BOX;
            double oz = ((i >> 2 & 1) * 2 - 1) * CAM_CLIP_BOX;
            Vec3 from = rayStart.add(ox, oy, oz);
            Vec3 to = desired.add(ox + oz, oy, oz);
            BlockHitResult hit = area.clip(new ClipContext(from, to,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, (Entity) null));
            if (hit.getType() != HitResult.Type.MISS) {
                double d = hit.getLocation().distanceTo(anchor);
                if (d < maxDist) {
                    maxDist = d;
                }
            }
        }
        if (maxDist >= dist) {
            return desired;
        }
        return anchor.add(dir.scale(maxDist));
    }
}
