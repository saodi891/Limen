package com.moonlightc.limen.entity.monster;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

public final class PamaEntity extends Monster implements GeoEntity {
    private static final float MAX_SINGLE_HIT_DAMAGE = 30.0F;
    private static final float SKILL_A_DAMAGE_THRESHOLD = 30.0F;
    private static final int SKILL_A_INVULNERABILITY_TICKS = 20 * 20;
    private static final double DETECTOR_COMMAND_RANGE = 20.0D;
    public static final double COLLISION_WIDTH = 32.0D;
    public static final double COLLISION_HEIGHT = 27.0D;
    public static final double COLLISION_DEPTH = 1.0D;
    public static final double PROTECTION_MARGIN = 3.0D;
    private static final double PROTECTION_SEARCH_MARGIN = COLLISION_WIDTH / 2.0D + PROTECTION_MARGIN + 1.0D;
    private static final WeakHashMap<Level, Set<PamaEntity>> LOADED_PAMA = new WeakHashMap<>();

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private float damageSinceSkillA;
    private int skillAInvulnerabilityTicks;

    public PamaEntity(EntityType<? extends PamaEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected AABB makeBoundingBox() {
        return new AABB(
                this.getX() - COLLISION_WIDTH / 2.0D,
                this.getY(),
                this.getZ() - COLLISION_DEPTH / 2.0D,
                this.getX() + COLLISION_WIDTH / 2.0D,
                this.getY() + COLLISION_HEIGHT,
                this.getZ() + COLLISION_DEPTH / 2.0D
        );
    }

    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        // EntityDimensions only supports a square horizontal footprint; makeBoundingBox supplies
        // the exact 32 x 27 x 1 collision volume.
        return EntityDimensions.scalable((float) COLLISION_WIDTH, (float) COLLISION_HEIGHT);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(0.0F);
        this.setXRot(0.0F);
        this.yRotO = 0.0F;
        this.xRotO = 0.0F;
        this.yHeadRot = 0.0F;
        this.yHeadRotO = 0.0F;
        this.yBodyRot = 0.0F;
        this.yBodyRotO = 0.0F;
        if (!this.level().isClientSide && this.skillAInvulnerabilityTicks > 0) {
            this.skillAInvulnerabilityTicks--;
        }
        this.bossEvent.setProgress(this.getMaxHealth() <= 0.0F ? 0.0F : this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void move(MoverType moverType, Vec3 movement) {
        // PAMA remains anchored at its spawn position, including against entity and piston pushes.
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }

    @Override
    public boolean isPickable() {
        return this.isAlive();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        synchronized (LOADED_PAMA) {
            LOADED_PAMA.computeIfAbsent(this.level(), ignored ->
                    Collections.newSetFromMap(new IdentityHashMap<>())).add(this);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        synchronized (LOADED_PAMA) {
            Set<PamaEntity> entities = LOADED_PAMA.get(this.level());
            if (entities != null) {
                entities.remove(this);
                if (entities.isEmpty()) {
                    LOADED_PAMA.remove(this.level());
                }
            }
        }
        super.onRemovedFromWorld();
    }

    public static Set<PamaEntity> loadedIn(Level level) {
        synchronized (LOADED_PAMA) {
            Set<PamaEntity> entities = LOADED_PAMA.get(level);
            return entities == null ? Set.of() : Set.copyOf(entities);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.skillAInvulnerabilityTicks > 0 || amount <= 0.0F) {
            return false;
        }

        float healthBefore = this.getHealth();
        boolean hurt = super.hurt(source, Math.min(amount, MAX_SINGLE_HIT_DAMAGE));
        if (hurt && !this.level().isClientSide) {
            float healthLost = Math.max(0.0F, healthBefore - this.getHealth());
            this.damageSinceSkillA += healthLost;
            if (this.damageSinceSkillA >= SKILL_A_DAMAGE_THRESHOLD && this.isAlive()) {
                this.damageSinceSkillA -= SKILL_A_DAMAGE_THRESHOLD;
                this.activateSkillA();
            }
        }
        return hurt;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return this.skillAInvulnerabilityTicks > 0 || super.isInvulnerableTo(source);
    }

    private void activateSkillA() {
        this.skillAInvulnerabilityTicks = SKILL_A_INVULNERABILITY_TICKS;
        double rangeSqr = DETECTOR_COMMAND_RANGE * DETECTOR_COMMAND_RANGE;
        AABB detectorSearchBox = this.getBoundingBox().inflate(DETECTOR_COMMAND_RANGE);
        for (PamaDetectorEntity detector : this.level().getEntitiesOfClass(
                PamaDetectorEntity.class, detectorSearchBox,
                candidate -> candidate.isAlive() && candidate.distanceToSqr(this) <= rangeSqr)) {
            detector.commandRecallTo(this);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    public static boolean isBlockProtected(Level level, BlockPos pos) {
        AABB blockBox = new AABB(pos);
        AABB searchBox = blockBox.inflate(PROTECTION_SEARCH_MARGIN);
        return !level.getEntitiesOfClass(PamaEntity.class, searchBox).stream()
                .filter(PamaEntity::isAlive)
                .map(entity -> entity.getBoundingBox().inflate(PROTECTION_MARGIN))
                .noneMatch(protectedBox -> protectedBox.intersects(blockBox));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("DamageSinceSkillA", this.damageSinceSkillA);
        tag.putInt("SkillAInvulnerabilityTicks", this.skillAInvulnerabilityTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damageSinceSkillA = tag.getFloat("DamageSinceSkillA");
        this.skillAInvulnerabilityTicks = tag.getInt("SkillAInvulnerabilityTicks");
    }
}
