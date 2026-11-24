package com.moonlightc.limen.entity.projectile;

import com.moonlightc.limen.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class CoilFieldEntity extends Entity {
    public static final float EFFECT_RADIUS = 1.5F;
    public static final int LIFETIME_TICKS = 20 * 6;
    private static final int DAMAGE_INTERVAL_TICKS = 20;

    private int remainingTicks = LIFETIME_TICKS;
    private int damageCooldownTicks;
    private UUID anchorEntityId;
    @Nullable
    private UUID ownerUUID;

    public CoilFieldEntity(EntityType<? extends CoilFieldEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public CoilFieldEntity(Level level, double x, double y, double z) {
        this(ModEntities.COIL_FIELD.get(), level);
        this.setPos(x, y, z);
    }

    public void setOwner(@Nullable Entity owner) {
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    public CoilFieldEntity(Level level, Entity anchor) {
        this(level, anchor, null);
    }

    public CoilFieldEntity(Level level, Entity anchor, @Nullable Entity owner) {
        this(ModEntities.COIL_FIELD.get(), level);
        this.anchorEntityId = anchor.getUUID();
        this.ownerUUID = owner == null ? null : owner.getUUID();
        this.updateAnchorPosition(anchor);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.remainingTicks = tag.getInt("RemainingTicks");
        this.damageCooldownTicks = tag.getInt("DamageCooldown");
        if (tag.hasUUID("AnchorEntity")) {
            this.anchorEntityId = tag.getUUID("AnchorEntity");
        }
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("RemainingTicks", this.remainingTicks);
        tag.putInt("DamageCooldown", this.damageCooldownTicks);
        if (this.anchorEntityId != null) {
            tag.putUUID("AnchorEntity", this.anchorEntityId);
        }
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);

        Entity anchor = this.getAnchorEntity();
        if (anchor != null) {
            this.updateAnchorPosition(anchor);
        }

        if (this.level().isClientSide) {
            this.spawnFieldParticles();
        } else {
            this.tickDamage();
        }

        this.remainingTicks--;
        if (this.remainingTicks <= 0) {
            this.discard();
        }
    }

    private void tickDamage() {
        if (this.damageCooldownTicks > 0) {
            this.damageCooldownTicks--;
            return;
        }

        this.damageCooldownTicks = DAMAGE_INTERVAL_TICKS;
        AABB bounds = this.getBoundingBox().inflate(EFFECT_RADIUS);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, bounds, LivingEntity::isAlive);
        DamageSource source = this.damageSources().fellOutOfWorld();

        for (LivingEntity target : targets) {
            if ((this.ownerUUID == null || !this.ownerUUID.equals(target.getUUID()))
                    && this.overlapsShockSphere(target.getBoundingBox())) {
                Vec3 previousMotion = target.getDeltaMovement();
                target.hurt(source, 1.0F);
                target.setDeltaMovement(previousMotion);
                target.hurtMarked = false;
            }
        }
    }

    private boolean overlapsShockSphere(AABB box) {
        double clampedX = Mth.clamp(this.getX(), box.minX, box.maxX);
        double clampedY = Mth.clamp(this.getY(), box.minY, box.maxY);
        double clampedZ = Mth.clamp(this.getZ(), box.minZ, box.maxZ);
        double dx = this.getX() - clampedX;
        double dy = this.getY() - clampedY;
        double dz = this.getZ() - clampedZ;
        return dx * dx + dy * dy + dz * dz <= EFFECT_RADIUS * EFFECT_RADIUS;
    }

    private void spawnFieldParticles() {
        for (int i = 0; i < 4; i++) {
            double theta = this.random.nextDouble() * (Math.PI * 2.0D);
            double phi = (this.random.nextDouble() - 0.5D) * Math.PI;
            double radius = this.random.nextDouble() * EFFECT_RADIUS;
            double x = this.getX() + Math.cos(theta) * Math.cos(phi) * radius;
            double y = this.getY() + Math.sin(phi) * radius;
            double z = this.getZ() + Math.sin(theta) * Math.cos(phi) * radius;
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, 0.01D, 0.0D);
        }

        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 8, EFFECT_RADIUS * 0.5D, EFFECT_RADIUS * 0.3D, EFFECT_RADIUS * 0.5D, 0.0D);
        }
    }

    private Entity getAnchorEntity() {
        if (this.anchorEntityId == null) {
            return null;
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(this.anchorEntityId);
        }

        return null;
    }

    private void updateAnchorPosition(Entity anchor) {
        this.setPos(anchor.getX(), anchor.getBoundingBox().getCenter().y, anchor.getZ());
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
