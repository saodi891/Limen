package com.moonlightc.limen.entity.misc;

import com.moonlightc.limen.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Technical, non-collidable entity released from ENIAC's hand during its revive. It plays the {@code relink}
 * animation once, then shatters into particles and vanishes. Purely visual: no AI, physics, or gameplay effect.
 */
public class TotemOfRelinkEntity extends Entity implements GeoEntity {
    // Length of the relink clip in ticks; the totem shatters when it elapses. Tune to match the animation.
    public static final int LIFETIME_TICKS = 30;
    private static final RawAnimation RELINK =
            RawAnimation.begin().thenPlayAndHold("animation.totem_of_relink.relink");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public TotemOfRelinkEntity(EntityType<? extends TotemOfRelinkEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public TotemOfRelinkEntity(Level level, double x, double y, double z) {
        this(ModEntities.TOTEM_OF_RELINK.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        if (!this.level().isClientSide && this.tickCount >= LIFETIME_TICKS) {
            this.shatter();
        }
    }

    // #da0205 — replaces the totem's default green burst with the relink red.
    private static final DustParticleOptions RELINK_DUST =
            new DustParticleOptions(new Vector3f(218f / 255f, 2f / 255f, 5f / 255f), 1.2f);

    private void shatter() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(RELINK_DUST,
                    this.getX(), this.getY() + 0.5D, this.getZ(), 40, 0.3D, 0.5D, 0.3D, 0.15D);
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    this.getX(), this.getY() + 0.5D, this.getZ(), 12, 0.2D, 0.4D, 0.2D, 0.05D);
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.0F, 1.2F);
        this.discard();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "relink", 0, state -> state.setAndContinue(RELINK)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
