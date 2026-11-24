package com.moonlightc.limen.entity.monster;

import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public class TowerGuardEntity extends Vindicator implements GeoEntity {
    private static final String TAG_NORMAL_HITS = "TowerGuardNormalHits";
    private static final String TAG_DASH_COOLDOWN = "TowerGuardDashCooldown";
    private static final EntityDataAccessor<Boolean> DATA_DASH_ANIMATING =
            SynchedEntityData.defineId(TowerGuardEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tower_guard.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tower_guard.walk");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("animation.tower_guard.charge");
    private static final RawAnimation BASIC_ATTACK = RawAnimation.begin().thenPlay("animation.tower_guard.basic_attack");
    private static final RawAnimation SHIELD_BREAK = RawAnimation.begin().thenPlay("animation.tower_guard.shield_break");

    private static final int NORMAL_HITS_PER_AOE = 3;
    private static final double DASH_DAMAGE_MULTIPLIER = 1.2D;
    private static final double AOE_HORIZONTAL_RANGE = 2.0D;
    private static final double AOE_VERTICAL_RANGE = 0.5D;
    private static final float AOE_DAMAGE_MULTIPLIER = 0.9F;

    private static final int DASH_LOCK_TICKS = 6;
    private static final int DASH_DURATION_TICKS = 80;
    private static final int DASH_COOLDOWN_TICKS = 10 * 20;
    private static final double DASH_BASE_SPEED = 0.28D;
    private static final double DASH_MAX_MULTIPLIER = 3.0D;
    private static final double DASH_ACCELERATION_PER_TICK = 0.15D;
    private static final double DASH_KNOCKBACK_SPEED = 5.1D / 20.0D;
    private static final double DASH_DISMOUNT_SPEED = 7.5D / 20.0D;
    private static final float DASH_KNOCKBACK_STRENGTH = 1.15F;

    private static final double ATTACK_BOX_HORIZONTAL_INFLATION = 0.2D;
    private static final double ATTACK_BOX_VERTICAL_INFLATION = 0.5D;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int successfulNormalHits;
    private int dashCooldownTicks;

    public TowerGuardEntity(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(false);
        this.equipRoyalPolearm();
        this.applyDifficultyDamage(level.getDifficulty());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_DASH_ANIMATING, false);
    }

    private boolean isDashAnimating() {
        return this.entityData.get(DATA_DASH_ANIMATING);
    }

    private void setDashAnimating(boolean animating) {
        this.entityData.set(DATA_DASH_ANIMATING, animating);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 12.0D)
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> goal instanceof MeleeAttackGoal);
        this.goalSelector.addGoal(1, new TowerGuardDashGoal(this));
        this.goalSelector.addGoal(4, new TowerGuardMeleeGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
        this.equipRoyalPolearm();
        this.applyDifficultyDamage(difficulty.getDifficulty());
        return data;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.equipRoyalPolearm();
    }

    private void equipRoyalPolearm() {
        ItemStack polearm = new ItemStack(ModItems.ROYAL_POLEARM.get());
        // The guard's weapon is visual equipment only. An explicit empty modifier list prevents the
        // player's royal-polearm damage and reach modifiers from changing this mob's configured stats.
        polearm.getOrCreateTag().put("AttributeModifiers", new ListTag());
        this.setItemSlot(EquipmentSlot.MAINHAND, polearm);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private void applyDifficultyDamage(Difficulty difficulty) {
        if (this.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            return;
        }
        double totalDamage = switch (difficulty) {
            case HARD -> 26.0D;
            case NORMAL -> 20.0D;
            default -> 14.0D;
        };
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(totalDamage);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.dashCooldownTicks > 0) {
            this.dashCooldownTicks--;
        }
    }

    private boolean performAttack(LivingEntity target) {
        if (!this.canReachWithAttackBox(target)) {
            return false;
        }

        if (this.successfulNormalHits >= NORMAL_HITS_PER_AOE) {
            this.successfulNormalHits = 0;
            this.releaseAoeAttack();
            return true;
        }

        boolean harmed = super.doHurtTarget(target);
        if (harmed) {
            this.successfulNormalHits++;
        }
        return harmed;
    }

    private void releaseAoeAttack() {
        DamageSource source = this.damageSources().mobAttack(this);
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * AOE_DAMAGE_MULTIPLIER;
        AABB area = this.getBoundingBox().inflate(AOE_HORIZONTAL_RANGE, AOE_VERTICAL_RANGE, AOE_HORIZONTAL_RANGE);

        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, area, this::canHitWithAoe)) {
            boolean shieldBlocked = target instanceof Player player
                    && player.isBlocking() && player.isDamageSourceBlocked(source);
            int previousInvulnerability = target.invulnerableTime;
            target.invulnerableTime = 0;
            boolean harmed = target.hurt(source, damage);
            if (!harmed) {
                target.invulnerableTime = previousInvulnerability;
            }
            if (shieldBlocked) {
                ((Player) target).disableShield(true);
            }
        }
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
    }

    private boolean canHitWithAoe(LivingEntity target) {
        return target != this && target.isAlive()
                && !isProtectedIllager(target)
                && !this.isAlliedTo(target)
                && this.hasLineOfSight(target);
    }

    private static boolean isProtectedIllager(LivingEntity target) {
        if (!target.getType().is(LimenEntityTypeTags.ILLAGER_FACTION)) {
            return false;
        }
        return !(target instanceof OwnableEntity ownable) || ownable.getOwnerUUID() == null;
    }

    private boolean canReachWithAttackBox(LivingEntity target) {
        AABB attackBox = this.getBoundingBox().inflate(
                ATTACK_BOX_HORIZONTAL_INFLATION,
                ATTACK_BOX_VERTICAL_INFLATION,
                ATTACK_BOX_HORIZONTAL_INFLATION);
        return target.isAlive() && attackBox.intersects(target.getBoundingBox()) && this.hasLineOfSight(target);
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity instanceof LivingEntity living && isProtectedIllager(living)) {
            return true;
        }
        return super.isAlliedTo(entity);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_NORMAL_HITS, this.successfulNormalHits);
        tag.putInt(TAG_DASH_COOLDOWN, this.dashCooldownTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.successfulNormalHits = Mth.clamp(tag.getInt(TAG_NORMAL_HITS), 0, NORMAL_HITS_PER_AOE);
        this.dashCooldownTicks = Math.max(0, tag.getInt(TAG_DASH_COOLDOWN));
        this.equipRoyalPolearm();
    }

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {
        // May join a raid, but deliberately receives no raid-wave equipment enchantment.
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tower_guard", 5, state ->
                state.setAndContinue(this.isDashAnimating() ? CHARGE : state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "actions", 0, state -> PlayState.STOP)
                .triggerableAnim("basic_attack", BASIC_ATTACK)
                .triggerableAnim("shield_break", SHIELD_BREAK));
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.triggerAnim("actions", "basic_attack");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    private static final class TowerGuardMeleeGoal extends MeleeAttackGoal {
        private final TowerGuardEntity guard;

        private TowerGuardMeleeGoal(TowerGuardEntity guard) {
            super(guard, 1.0D, false);
            this.guard = guard;
        }

        @Override
        public void start() {
            super.start();
            this.guard.setAggressive(true);
        }

        @Override
        public void stop() {
            super.stop();
            this.guard.setAggressive(false);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distanceToTargetSqr) {
            if (this.isTimeToAttack() && this.guard.canReachWithAttackBox(target)) {
                this.resetAttackCooldown();
                this.guard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.guard.performAttack(target);
            }
        }
    }

    private static final class TowerGuardDashGoal extends Goal {
        private static final int PHASE_LOCK = 0;
        private static final int PHASE_CHARGE = 1;

        private final TowerGuardEntity guard;
        private LivingEntity target;
        private int phase;
        private int timer;
        private double directionX;
        private double directionZ;
        private double speedMultiplier;
        private boolean started;

        private TowerGuardDashGoal(TowerGuardEntity guard) {
            this.guard = guard;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity currentTarget = this.guard.getTarget();
            if (this.guard.dashCooldownTicks > 0 || currentTarget == null || !currentTarget.isAlive()) {
                return false;
            }
            this.target = currentTarget;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.started && this.target != null && this.target.isAlive() && this.timer > 0;
        }

        @Override
        public void start() {
            this.started = true;
            this.phase = PHASE_LOCK;
            this.timer = DASH_LOCK_TICKS;
            this.speedMultiplier = 1.0D;
            this.guard.setDashAnimating(false);
            this.guard.setAggressive(true);
            this.guard.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.guard.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            if (this.phase == PHASE_LOCK) {
                this.guard.setDeltaMovement(0.0D, Math.min(this.guard.getDeltaMovement().y, 0.0D), 0.0D);
                if (--this.timer <= 0) {
                    this.phase = PHASE_CHARGE;
                    this.timer = DASH_DURATION_TICKS;
                    this.guard.setDashAnimating(true);
                }
                return;
            }

            double dx = this.target.getX() - this.guard.getX();
            double dz = this.target.getZ() - this.guard.getZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > 1.0E-4D) {
                this.directionX = dx / horizontalDistance;
                this.directionZ = dz / horizontalDistance;
            }
            this.speedMultiplier = Math.min(DASH_MAX_MULTIPLIER,
                    this.speedMultiplier + DASH_ACCELERATION_PER_TICK);
            double speed = DASH_BASE_SPEED * this.speedMultiplier;
            this.guard.setDeltaMovement(this.directionX * speed,
                    this.guard.getDeltaMovement().y, this.directionZ * speed);
            this.guard.hasImpulse = true;

            if (this.guard.canReachWithAttackBox(this.target)) {
                this.strike();
                return;
            }
            this.timer--;
        }

        private void strike() {
            DamageSource source = this.guard.damageSources().mobAttack(this.guard);
            if (this.target instanceof Player player && player.isBlocking()
                    && player.isDamageSourceBlocked(source)) {
                player.disableShield(true);
                this.guard.triggerAnim("actions", "shield_break");
                this.timer = 0;
                return;
            }

            Vec3 attackerDelta = this.guard.getDeltaMovement();
            Vec3 targetDelta = this.target.getDeltaMovement();
            double closingSpeed = (attackerDelta.x - targetDelta.x) * this.directionX
                    + (attackerDelta.z - targetDelta.z) * this.directionZ;
            float damage = Math.max(1.0F, (float) Math.ceil(
                    closingSpeed * 20.0D * DASH_DAMAGE_MULTIPLIER));
            boolean harmed = this.target.hurt(source, damage);
            if (harmed && closingSpeed > DASH_KNOCKBACK_SPEED) {
                this.target.knockback(DASH_KNOCKBACK_STRENGTH, -this.directionX, -this.directionZ);
                this.target.hurtMarked = true;
            }
            if (harmed && closingSpeed > DASH_DISMOUNT_SPEED) {
                this.target.stopRiding();
            }
            this.timer = 0;
        }

        @Override
        public void stop() {
            if (this.started) {
                this.guard.dashCooldownTicks = DASH_COOLDOWN_TICKS;
            }
            this.guard.setAggressive(false);
            this.guard.setDashAnimating(false);
            this.guard.setDeltaMovement(Vec3.ZERO);
            this.guard.getNavigation().stop();
            this.target = null;
            this.timer = 0;
            this.started = false;
        }
    }
}
