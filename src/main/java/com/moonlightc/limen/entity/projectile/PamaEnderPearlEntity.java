package com.moonlightc.limen.entity.projectile;

import com.moonlightc.limen.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Ender pearl thrown by ENIAC's skillB. Behaves like a vanilla pearl in flight (item visuals, gravity, can
 * be blocked by terrain) but, unlike {@link net.minecraft.world.entity.projectile.ThrownEnderpearl}, teleports
 * its non-player thrower to the impact point. Vanilla's pearl only teleports {@code ServerPlayer} owners, so
 * a mob-thrown vanilla pearl would simply vanish.
 */
public class PamaEnderPearlEntity extends net.minecraft.world.entity.projectile.ThrowableItemProjectile {
    public PamaEnderPearlEntity(EntityType<? extends PamaEnderPearlEntity> entityType, Level level) {
        super(entityType, level);
    }

    public PamaEnderPearlEntity(Level level, LivingEntity thrower) {
        super(ModEntities.PAMA_ENDER_PEARL.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }

        for (int i = 0; i < 32; i++) {
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.PORTAL,
                    this.getX(), this.getY() + this.random.nextDouble() * 2.0D, this.getZ(),
                    1, this.random.nextGaussian(), 0.0D, this.random.nextGaussian(), 0.0D);
        }

        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity thrower && thrower.isAlive() && !this.isRemoved()) {
            thrower.level().playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
            thrower.teleportTo(this.getX(), this.getY(), this.getZ());
            thrower.fallDistance = 0.0F;
            thrower.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        this.discard();
    }
}
