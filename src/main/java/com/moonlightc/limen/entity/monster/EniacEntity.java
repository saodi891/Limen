package com.moonlightc.limen.entity.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import com.moonlightc.limen.entity.projectile.ChargedCoilArrowEntity;
import com.moonlightc.limen.entity.misc.TotemOfRelinkEntity;
import com.moonlightc.limen.entity.projectile.PamaEnderPearlEntity;
import com.moonlightc.limen.entity.projectile.PamaTridentEntity;
import com.moonlightc.limen.registry.ModItems;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class EniacEntity extends Monster implements GeoEntity {
    private static final float BUILT_IN_PROTECTION = 20.0F;
    private static final double WALK_SPEED_MODIFIER = 1.0D;
    private static final double RUN_SPEED_MODIFIER = 1.3D;
    private static final EntityDimensions CRAWLING_DIMENSIONS = EntityDimensions.scalable(0.6F, 0.6F);
    // Revive animation is 4.5s (90 ticks); the timer matches so phase 2 begins exactly as the clip ends.
    private static final int REVIVE_DURATION_TICKS = 90;
    // 1.0s into the revive, ENIAC clears the relink totem from its hand and releases the totem entity.
    private static final int TOTEM_RELEASE_TICKS = 20;
    private static final float PHASE_TWO_HEALTH = 20.0F;
    // Player-equivalent melee reach (~3 blocks) instead of the shorter default mob reach.
    private static final double ATTACK_REACH = 3.0D;
    private static final int SKILL_A_COOLDOWN_TICKS = 30 * 20;
    // Give up the melee chase after 10s and finish the skill with the ranged trident throw instead.
    private static final int SKILL_A_CHASE_TIMEOUT_TICKS = 10 * 20;
    // The axe launch fires the instant skillAattack starts; this is how long ENIAC then holds the pose.
    private static final int SKILL_A_STRIKE_DELAY_TICKS = 7;
    // The trident leaves the instant the throw animation starts; this is how long ENIAC then holds the pose.
    private static final int SKILL_A_BOW_DELAY_TICKS = 10;
    // Iron-golem-style upward fling; strong enough that the target is thrown well clear of the ground.
    private static final double SKILL_A_LAUNCH = 2.0D;
    // Fired trident launch speed: twice a vanilla trident (2.5 -> 5.0).
    private static final float SKILL_A_TRIDENT_SPEED = 5.0F;

    private static final int SKILL_B_COOLDOWN_TICKS = 30 * 20;
    // Only worth teleporting in if the target is beyond normal melee range.
    private static final double SKILL_B_MIN_RANGE = 8.0D;
    private static final int SKILL_B_PEARL_COUNT = 3;
    private static final int SKILL_B_SLASHES_PER_ROUND = 3;
    private static final float SKILL_B_PEARL_SPEED = 1.5F;
    // Ticks to wait for the pearl to land/teleport before giving up on this round.
    private static final int SKILL_B_PEARL_TIMEOUT_TICKS = 40;
    // Three slashes must land inside ~1s. Each slash cycle = 1 windup tick + strike + recover,
    // so 3 * (1 + 4 + 1) = 18 ticks (0.9s). Keep the skillb_slash clip <= ~6 ticks (0.3s) so it
    // reads cleanly at this cadence instead of being cut off mid-swing.
    private static final int SKILL_B_SLASH_STRIKE_TICKS = 4;
    private static final int SKILL_B_SLASH_RECOVER_TICKS = 1;
    // Dedicated reach for the in-place three-slash — wider than normal melee. The pearl lands ENIAC beside
    // the target, but a target strafing between slashes would fall out of the 3-block reach and trigger a
    // false whiff -> crossbow break every round. This wider reach keeps a moving target inside the combo.
    private static final double SKILL_B_SLASH_REACH = 4.5D;
    private static final int SKILL_B_SLOW_TICKS = 5 * 20;
    // Third slash of a round delivers the accumulated Knockback II x3 punch.
    private static final double SKILL_B_KNOCKBACK = 1.5D;
    // Crossbow interception on a whiff: spectral arrow then two coil arrows, ~0.05s (1 tick) apart.
    private static final int SKILL_B_INTERCEPT_SHOTS = 3;
    private static final int SKILL_B_INTERCEPT_INTERVAL_TICKS = 1;
    private static final float SKILL_B_ARROW_SPEED = 3.0F;

    // Leap attack: whenever the aggro target sits more than this many blocks overhead and out of melee
    // reach, ENIAC immediately jumps high enough to pull it into range, landing a Knockback II-ish punch.
    private static final double LEAP_TRIGGER_HEIGHT = 1.5D;
    private static final double LEAP_MAX_HORIZONTAL = 5.0D;
    private static final double LEAP_KNOCKBACK = 1.0D;
    private static final int LEAP_COOLDOWN_TICKS = 40;
    private static final int LEAP_MAX_AIR_TICKS = 30;
    // Vanilla jump velocity (~0.42 clears ~1.25 blocks) is the floor; the leap scales up toward the
    // target's height and is capped here so a very high perch can't fling ENIAC absurdly far.
    private static final double PLAYER_JUMP_VELOCITY = 0.42D;
    private static final double LEAP_MAX_LAUNCH_VELOCITY = 1.1D;
    // Non-skill melee chase: hop forward once per second to close on a still-out-of-reach target faster.
    private static final int CHASE_HOP_INTERVAL_TICKS = 20;
    private static final double CHASE_HOP_FORWARD = 0.3D;

    // How fast the body/head swing to square up on the aggro target, in degrees per tick.
    private static final float BODY_TURN_DEGREES_PER_TICK = 30.0F;

    // Phase-1 skillC: switches to the powered polearm and dashes at the highest-health enemy five times.
    // Each dash is a velocity-driven charge that accelerates from ENIAC's run pace up to 3x, deals powered-
    // polearm charge-style (speed-scaled) damage on contact, then charges THROUGH and decelerates instead of
    // stopping. A shield-blocked strike is the only thing that halts it, to break the shield with an axe.
    private static final int SKILL_C_COOLDOWN_TICKS = 30 * 20;
    private static final int SKILL_C_DASH_COUNT = 5;
    // skillC1 windup: ENIAC stands still and locks onto the highest-health target before each dash.
    private static final int SKILL_C_LOCK_TICKS = 20;
    // Abandon a dash if the target can't be reached within this window (target fled/unreachable).
    private static final int SKILL_C_APPROACH_TIMEOUT_TICKS = 100;
    // Pause on a shield-blocked strike while the axe breaks the shield.
    private static final int SKILL_C_SHIELD_BREAK_TICKS = 10;
    // Charge tuning (blocks/tick). ENIAC starts a dash at its run pace and ramps the multiplier up to the cap,
    // then bleeds it back down through the follow-through so it coasts past the target.
    private static final double SKILL_C_CHARGE_BASE_SPEED = 0.35D;
    private static final double SKILL_C_CHARGE_MAX_MULTIPLIER = 3.0D;
    private static final double SKILL_C_CHARGE_ACCEL_PER_TICK = 0.15D;
    private static final double SKILL_C_CHARGE_DECEL_PER_TICK = 0.18D;
    // End the follow-through (and the dash) once deceleration drops the charge below this multiple of run pace.
    private static final double SKILL_C_FOLLOW_THROUGH_MIN_MULTIPLIER = 0.5D;
    // Mirror of RoyalPolearmEvents charge knockback: directional knockback above this speed, this strength.
    private static final double SKILL_C_CHARGE_KNOCKBACK_SPEED = 5.1D / 20.0D;
    private static final float SKILL_C_CHARGE_KNOCKBACK_STRENGTH = 1.15F;
    // skillA dash speed as a transient modifier (Speed-II-equivalent) instead of a potion effect.
    private static final UUID SKILL_A_SPEED_UUID = UUID.fromString("2c9d7e14-3f5b-4a6c-8d1e-7b9f0a2c4d6e");
    private static final AttributeModifier SKILL_A_SPEED_MODIFIER = new AttributeModifier(
            SKILL_A_SPEED_UUID, "skillA dash speed", 0.4D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.eniac.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.eniac.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.eniac.run");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.eniac.attack");
    private static final RawAnimation SKILL_A_RUN = RawAnimation.begin().thenLoop("animation.eniac.skillArun");
    private static final RawAnimation SKILL_A_ATTACK = RawAnimation.begin().thenPlay("animation.eniac.skillAattack");
    private static final RawAnimation SKILL_B_LEAP = RawAnimation.begin().thenPlay("animation.eniac.skillb_leap");
    private static final RawAnimation SKILL_B_SLASH = RawAnimation.begin().thenPlay("animation.eniac.skillb_slash");
    private static final RawAnimation SKILL_B_SHOOT = RawAnimation.begin().thenPlay("animation.eniac.skillb_shoot");
    private static final RawAnimation REVIVE = RawAnimation.begin().thenPlayAndHold("animation.eniac.revive");
    private static final RawAnimation SKILL_C1 = RawAnimation.begin().thenLoop("animation.eniac.skillC1");
    private static final RawAnimation WALK2 = RawAnimation.begin().thenLoop("animation.eniac.walk2");

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(EniacEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_REVIVING =
            SynchedEntityData.defineId(EniacEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SKILL_A_RUNNING =
            SynchedEntityData.defineId(EniacEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SKILL_B_RUNNING =
            SynchedEntityData.defineId(EniacEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SKILL_C_RUNNING =
            SynchedEntityData.defineId(EniacEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    // Custom-rendered boss bar (see EniacBossBarOverlay). Named with the entity type's translation key so the
    // client can identify ENIAC's bar even if the mob is renamed with a name tag.
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getType().getDescription(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private int reviveTicks;
    private int skillACooldown;
    private int skillBCooldown;
    private int skillCCooldown;
    private int leapCooldown;
    // Yaw locked in place for the duration of the revive animation so the body/head never rotate.
    private float reviveYaw;

    public EniacEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(false);
        // ENIAC swaps its own weapons during skills; never drop them on death.
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        // finalizeSpawn is skipped by /summon and by NBT-loaded entities; equip here so ENIAC
        // always starts holding the sword bone instead of empty-handed.
        this.equipBaseWeapon();
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType spawnType,
            net.minecraft.world.entity.SpawnGroupData spawnData,
            CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData data =
                super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
        this.equipBaseWeapon();
        return data;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, 1);
        this.entityData.define(DATA_REVIVING, false);
        this.entityData.define(DATA_SKILL_A_RUNNING, false);
        this.entityData.define(DATA_SKILL_B_RUNNING, false);
        this.entityData.define(DATA_SKILL_C_RUNNING, false);
    }

    public boolean isSkillARunning() {
        return this.entityData.get(DATA_SKILL_A_RUNNING);
    }

    private void setSkillARunning(boolean running) {
        this.entityData.set(DATA_SKILL_A_RUNNING, running);
    }

    public boolean isSkillBRunning() {
        return this.entityData.get(DATA_SKILL_B_RUNNING);
    }

    private void setSkillBRunning(boolean running) {
        this.entityData.set(DATA_SKILL_B_RUNNING, running);
    }

    public boolean isSkillCRunning() {
        return this.entityData.get(DATA_SKILL_C_RUNNING);
    }

    private void setSkillCRunning(boolean running) {
        this.entityData.set(DATA_SKILL_C_RUNNING, running);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    private void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
    }

    public boolean isReviving() {
        return this.entityData.get(DATA_REVIVING);
    }

    private void setReviving(boolean reviving) {
        this.entityData.set(DATA_REVIVING, reviving);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.13D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SkillAGoal(this));
        this.goalSelector.addGoal(0, new SkillBGoal(this));
        this.goalSelector.addGoal(0, new SkillCGoal(this));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LeapAtHighTargetGoal(this));
        this.goalSelector.addGoal(2, new PlayerReachMeleeAttackGoal(this, RUN_SPEED_MODIFIER));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, WALK_SPEED_MODIFIER));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        this.setZza(speed == 0.0F ? 0.0F : 1.0F);
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
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (this.skillACooldown > 0) {
            this.skillACooldown--;
        }
        if (this.skillBCooldown > 0) {
            this.skillBCooldown--;
        }
        if (this.skillCCooldown > 0) {
            this.skillCCooldown--;
        }
        if (this.leapCooldown > 0) {
            this.leapCooldown--;
        }

        if (this.isReviving()) {
            // Frozen while the revive animation plays: no movement, no target, no navigation, and the
            // body/head are pinned to the yaw captured at death so ENIAC cannot turn or track anyone.
            this.setDeltaMovement(0.0D, Math.min(this.getDeltaMovement().y, 0.0D), 0.0D);
            this.setTarget(null);
            this.setLastHurtByMob(null);
            this.getNavigation().stop();
            this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
            this.setYRot(this.reviveYaw);
            this.yRotO = this.reviveYaw;
            this.setYHeadRot(this.reviveYaw);
            this.setYBodyRot(this.reviveYaw);
            this.yHeadRotO = this.reviveYaw;
            this.yBodyRotO = this.reviveYaw;
            // 1.0s in: drop the totem it has been holding and release the technical totem entity at the hand.
            if (REVIVE_DURATION_TICKS - this.reviveTicks == TOTEM_RELEASE_TICKS) {
                this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                this.releaseRelinkTotem();
            }
            if (--this.reviveTicks <= 0) {
                this.setReviving(false);
                this.setNoAi(false);
                this.setPhase(2);
                this.setHealth(PHASE_TWO_HEALTH);
                this.equipBaseWeapon();
            }
            return;
        }

        Pose wantedPose = this.isInWater() || !this.canEnterPose(Pose.STANDING)
                ? Pose.SWIMMING
                : Pose.STANDING;
        if (this.getPose() != wantedPose) {
            this.setPose(wantedPose);
            this.getNavigation().recomputePath();
        }

        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            this.faceTarget(currentTarget);
        }
    }

    /**
     * Squares ENIAC's rendered body (and head) up on its target. GeckoLib renders the body by {@code yBodyRot},
     * so driving that field — not just the LookControl head — is what makes the whole model face who it fights.
     * Run at the tail of {@link #aiStep()} so it wins over the movement/look updates from {@code super.aiStep()};
     * {@code yRot} is left to navigation so pathing around obstacles is unaffected.
     */
    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float desiredYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        this.yBodyRot = approachYaw(this.yBodyRot, desiredYaw, BODY_TURN_DEGREES_PER_TICK);
        this.yHeadRot = approachYaw(this.yHeadRot, desiredYaw, BODY_TURN_DEGREES_PER_TICK);
    }

    private static float approachYaw(float current, float target, float maxDelta) {
        float delta = wrapDegrees(target - current);
        if (delta > maxDelta) {
            delta = maxDelta;
        } else if (delta < -maxDelta) {
            delta = -maxDelta;
        }
        return current + delta;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Invulnerable while reviving so it cannot be killed mid-animation, except /kill and the void.
        if (this.isReviving() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        // Cannot be shoved around by colliding entities while the revive animation plays.
        return !this.isReviving() && super.isPushable();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (this.isReviving()) {
            return;
        }
        super.knockback(strength, x, z);
    }

    @Override
    public void die(DamageSource source) {
        // First death in phase 1 is intercepted: play the revive animation instead, then return at 20 HP.
        if (!this.level().isClientSide && this.getPhase() == 1 && !this.isReviving()
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.startRevive();
            return;
        }
        super.die(source);
    }

    private void startRevive() {
        this.setReviving(true);
        // Full AI freeze: no attacking, no navigation, and the look control cannot turn the head.
        this.setNoAi(true);
        this.reviveYaw = this.getYRot();
        this.reviveTicks = REVIVE_DURATION_TICKS;
        this.deathTime = 0;
        this.setHealth(1.0F);
        this.setLastHurtByMob(null);
        this.setTarget(null);
        this.setDeltaMovement(Vec3.ZERO);
        this.hurtMarked = true;
        // Holds the relink totem in its main hand (empty offhand) for the first 1.0s of the revive.
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.TOTEM_OF_RELINK.get()));
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private void releaseRelinkTotem() {
        float yawRad = this.reviveYaw * ((float) Math.PI / 180.0F);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        // Perpendicular (to ENIAC's right) so the totem appears at the main-hand side rather than dead-center.
        double rightX = -forwardZ;
        double rightZ = forwardX;
        double handX = this.getX() + forwardX * 0.4D + rightX * 0.35D;
        double handY = this.getY() + 1.2D;
        double handZ = this.getZ() + forwardZ * 0.4D + rightZ * 0.35D;
        TotemOfRelinkEntity totem = new TotemOfRelinkEntity(this.level(), handX, handY, handZ);
        totem.setYRot(this.reviveYaw);
        this.level().addFreshEntity(totem);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", this.getPhase());
        tag.putBoolean("Reviving", this.isReviving());
        tag.putInt("ReviveTicks", this.reviveTicks);
        tag.putFloat("ReviveYaw", this.reviveYaw);
        tag.putInt("SkillACooldown", this.skillACooldown);
        tag.putInt("SkillBCooldown", this.skillBCooldown);
        tag.putInt("SkillCCooldown", this.skillCCooldown);
        tag.putInt("LeapCooldown", this.leapCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setPhase(tag.contains("Phase") ? tag.getInt("Phase") : 1);
        this.setReviving(tag.getBoolean("Reviving"));
        this.reviveTicks = tag.getInt("ReviveTicks");
        this.reviveYaw = tag.getFloat("ReviveYaw");
        this.skillACooldown = tag.getInt("SkillACooldown");
        this.skillBCooldown = tag.getInt("SkillBCooldown");
        this.skillCCooldown = tag.getInt("SkillCCooldown");
        this.leapCooldown = tag.getInt("LeapCooldown");
    }

    /** Player-equivalent melee reach used by both the normal attack goal and skillA's approach check. */
    private double getPlayerReachSqr(LivingEntity target) {
        return ATTACK_REACH * ATTACK_REACH + target.getBbWidth();
    }

    /**
     * The entity ENIAC is already locked onto (set by HurtByTargetGoal / NearestAttackableTargetGoal),
     * used as the skill target when no player candidate is around so skills also fire against other mobs.
     */
    private LivingEntity currentAggroTarget() {
        LivingEntity target = this.getTarget();
        return (target != null && target.isAlive() && this.hasLineOfSight(target)) ? target : null;
    }

    /**
     * Picks the hostile target with the lowest armour value (ties resolve to the nearest player). Falls back
     * to ENIAC's current aggro target (e.g. a mob it is retaliating against) when no player is in range.
     */
    private LivingEntity findLowestArmorTarget() {
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB bounds = this.getBoundingBox().inflate(range);
        List<Player> candidates = this.level().getEntitiesOfClass(Player.class, bounds,
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && this.hasLineOfSight(player));
        return candidates.stream()
                .min(Comparator.comparingInt(Player::getArmorValue)
                        .thenComparingDouble(this::distanceToSqr))
                .map(LivingEntity.class::cast)
                .orElseGet(this::currentAggroTarget);
    }

    /**
     * Deals a melee hit with the currently held weapon (skillA equips a Sharpness VI pama_axe, so
     * {@link #doHurtTarget} automatically applies netherite-axe + Sharpness VI damage) plus an
     * iron-golem-style upward launch that leaves the target airborne.
     */
    private void performSkillALaunch(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        if (this.doHurtTarget(target)) {
            double resist = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double factor = Math.max(0.0D, 1.0D - resist);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, SKILL_A_LAUNCH * factor, 0.0D));
            target.hurtMarked = true;
        }
        this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
    }

    /** Equips a fresh Sharpness VI pama_axe for skillA's melee phase. */
    private void equipSkillAAxe() {
        ItemStack axe = new ItemStack(ModItems.PAMA_AXE.get());
        axe.enchant(Enchantments.SHARPNESS, 6);
        this.setItemSlot(EquipmentSlot.MAINHAND, axe);
    }

    /** Base loadout: pama_sword in the main hand. Its netherite-tier stats drive the normal melee attack. */
    private void equipBaseWeapon() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PAMA_SWORD.get()));
    }

    /** Prefers an airborne hostile (the just-launched target usually), else the locked target. */
    private LivingEntity chooseTridentTarget(LivingEntity locked) {
        if (locked != null && locked.isAlive() && !locked.onGround()) {
            return locked;
        }
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        List<Player> airborne = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(range),
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.onGround());
        return airborne.stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .map(LivingEntity.class::cast)
                .orElse(locked);
    }

    /**
     * Fires a pama_trident at the target, leading its movement so a moving/airborne target is easy to hit.
     * The projectile carries the usual 2x-trident stats plus its impact shock wave.
     */
    private void fireSkillATrident(LivingEntity locked) {
        LivingEntity target = this.chooseTridentTarget(locked);
        if (target == null || !target.isAlive()) {
            return;
        }
        PamaTridentEntity trident = new PamaTridentEntity(this.level(), this, new ItemStack(ModItems.PAMA_TRIDENT.get()));
        Vec3 from = this.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double travelTicks = from.distanceTo(targetCenter) / SKILL_A_TRIDENT_SPEED;
        Vec3 predicted = targetCenter.add(target.getDeltaMovement().scale(travelTicks));
        Vec3 direction = predicted.subtract(from);
        trident.shoot(direction.x, direction.y, direction.z, SKILL_A_TRIDENT_SPEED, 0.0F);
        this.level().addFreshEntity(trident);
        this.playSound(SoundEvents.TRIDENT_THROW, 1.0F, 1.0F);
    }

    /** skillB: pearl aimed at the target's feet so the teleport drops ENIAC right beside them. */
    private PamaEnderPearlEntity throwSkillBPearl(LivingEntity target) {
        PamaEnderPearlEntity pearl = new PamaEnderPearlEntity(this.level(), this);
        Vec3 from = this.getEyePosition();
        Vec3 direction = target.position().subtract(from);
        pearl.shoot(direction.x, direction.y, direction.z, SKILL_B_PEARL_SPEED, 1.0F);
        this.level().addFreshEntity(pearl);
        this.playSound(SoundEvents.ENDER_PEARL_THROW, 1.0F, 1.0F);
        return pearl;
    }

    /** Slowness on hit; stacks a level if the target already has it (Slowness I -> II -> ...). */
    private void applySkillBSlowness(LivingEntity target) {
        MobEffectInstance existing = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int amplifier = existing != null ? Math.min(existing.getAmplifier() + 1, 255) : 0;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SKILL_B_SLOW_TICKS, amplifier));
    }

    /**
     * Resolves one slash of a skillB round. Returns true if it connected. On a connecting knockback slash
     * (the third of the round) the accumulated Knockback II x3 punch is applied; the first two never knock back.
     */
    private boolean resolveSkillBSlash(LivingEntity target, boolean knockbackSlash) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        double reachSqr = SKILL_B_SLASH_REACH * SKILL_B_SLASH_REACH + target.getBbWidth();
        if (this.distanceToSqr(target) > reachSqr) {
            return false;
        }
        boolean hit = this.doHurtTarget(target);
        if (hit) {
            this.applySkillBSlowness(target);
            if (knockbackSlash) {
                target.knockback(SKILL_B_KNOCKBACK, this.getX() - target.getX(), this.getZ() - target.getZ());
                target.hurtMarked = true;
            }
        }
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.0F);
        return hit;
    }

    /** Fires one interception shot at the target: first a spectral arrow, then charged coil arrows. */
    private void fireSkillBInterceptShot(int shotIndex, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        AbstractArrow arrow = shotIndex == 0
                ? new SpectralArrow(this.level(), this)
                : new ChargedCoilArrowEntity(this.level(), this);
        arrow.setOwner(this);
        Vec3 from = new Vec3(this.getX(), this.getEyeY() - 0.1D, this.getZ());
        Vec3 direction = target.getBoundingBox().getCenter().subtract(from);
        arrow.shoot(direction.x, direction.y, direction.z, SKILL_B_ARROW_SPEED, 1.0F);
        this.level().addFreshEntity(arrow);
        this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F);
    }

    private void equipSkillBCrossbow() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PAMA_CROSSBOW.get()));
    }

    /** skillC target pick: the highest-health hostile player, ties resolving to the nearest; else the aggro target. */
    private LivingEntity findHighestHealthTarget() {
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB bounds = this.getBoundingBox().inflate(range);
        List<Player> candidates = this.level().getEntitiesOfClass(Player.class, bounds,
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && this.hasLineOfSight(player));
        return candidates.stream()
                .max(Comparator.comparingDouble(Player::getHealth)
                        .thenComparingDouble(player -> -this.distanceToSqr(player)))
                .map(LivingEntity.class::cast)
                .orElseGet(this::currentAggroTarget);
    }

    private void equipPoweredPolearm() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.POWERED_POLEARM.get()));
    }

    /** skillA dash speed: a transient Speed-II-equivalent MOVEMENT_SPEED modifier (no potion effect). */
    private void applySkillASpeed() {
        var attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null && !attribute.hasModifier(SKILL_A_SPEED_MODIFIER)) {
            attribute.addTransientModifier(SKILL_A_SPEED_MODIFIER);
        }
    }

    private void removeSkillASpeed() {
        var attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(SKILL_A_SPEED_UUID);
        }
    }

    /**
     * skillC shield break: swaps to a pama_axe, replicates the (protected) vanilla shield-disable
     * (cooldown + stop using + shield-break event) and lands an axe hit before ENIAC resumes the dash.
     */
    private void breakTargetShield(LivingEntity target) {
        if (target instanceof Player player) {
            player.getCooldowns().addCooldown(Items.SHIELD, 100);
            player.stopUsingItem();
            this.level().broadcastEntityEvent(player, (byte) 30);
            this.playSound(SoundEvents.SHIELD_BREAK, 1.0F, 1.0F);
        }
        this.doHurtTarget(target);
    }

    private void equipSkillCAxe() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PAMA_AXE.get()));
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.SWIMMING ? CRAWLING_DIMENSIONS : super.getDimensions(pose);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return pose == Pose.SWIMMING ? 0.4F : 1.62F;
    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        float reduced = super.getDamageAfterMagicAbsorb(source, amount);
        if (reduced <= 0.0F
                || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return reduced;
        }
        return CombatRules.getDamageAfterMagicAbsorb(reduced, BUILT_IN_PROTECTION);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return source.type().effects().sound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "eniac", 5, state -> {
            if (this.isReviving()) {
                return state.setAndContinue(REVIVE);
            }
            if (this.isSkillARunning()) {
                return state.setAndContinue(SKILL_A_RUN);
            }
            if (this.isSkillCRunning()) {
                return state.setAndContinue(state.isMoving() ? WALK2 : SKILL_C1);
            }
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(this.isAggressive() ? RUN : WALK);
        }));
        controllers.add(new AnimationController<>(this, "actions", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("skillAattack", SKILL_A_ATTACK)
                .triggerableAnim("skillb_leap", SKILL_B_LEAP)
                .triggerableAnim("skillb_slash", SKILL_B_SLASH)
                .triggerableAnim("skillb_shoot", SKILL_B_SHOOT));
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        if (!this.level().isClientSide) {
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
        }
        this.triggerAnim("actions", "attack");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    /** Normal melee attack goal with player-equivalent reach and a sword-like swing cadence. */
    private static final class PlayerReachMeleeAttackGoal extends MeleeAttackGoal {
        // Netherite-sword speed (~1.6 attacks/s -> ~0.6s). The vanilla mob default of 20 ticks felt sluggish.
        private static final int NORMAL_ATTACK_INTERVAL_TICKS = 12;
        private final EniacEntity eniac;
        private int attackCooldown;
        private int hopCooldown;

        private PlayerReachMeleeAttackGoal(EniacEntity eniac, double speedModifier) {
            super(eniac, speedModifier, false);
            this.eniac = eniac;
        }

        @Override
        public boolean canUse() {
            return !this.eniac.isReviving() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.eniac.isReviving() && super.canContinueToUse();
        }

        @Override
        public void start() {
            super.start();
            this.attackCooldown = 0;
            this.hopCooldown = CHASE_HOP_INTERVAL_TICKS;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.attackCooldown > 0) {
                this.attackCooldown--;
            }
            this.tickChaseHop();
        }

        // Every 1s while still out of melee reach, hop forward toward the target so the chase closes faster.
        private void tickChaseHop() {
            if (this.hopCooldown > 0) {
                this.hopCooldown--;
            }
            LivingEntity target = this.eniac.getTarget();
            if (target == null || !this.eniac.onGround()
                    || this.eniac.distanceToSqr(target) <= this.getAttackReachSqr(target)) {
                return;
            }
            if (this.hopCooldown <= 0) {
                this.hopCooldown = CHASE_HOP_INTERVAL_TICKS;
                double dx = target.getX() - this.eniac.getX();
                double dz = target.getZ() - this.eniac.getZ();
                double horiz = Math.sqrt(dx * dx + dz * dz);
                Vec3 v = this.eniac.getDeltaMovement();
                double vx = horiz > 1.0E-4D ? dx / horiz * CHASE_HOP_FORWARD : 0.0D;
                double vz = horiz > 1.0E-4D ? dz / horiz * CHASE_HOP_FORWARD : 0.0D;
                this.eniac.setDeltaMovement(v.x + vx, PLAYER_JUMP_VELOCITY, v.z + vz);
                this.eniac.hasImpulse = true;
            }
        }

        // Self-managed cadence: the vanilla cooldown field is private, so gate on our own counter instead.
        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
            if (this.attackCooldown <= 0 && distToTargetSqr <= this.getAttackReachSqr(target)) {
                this.attackCooldown = NORMAL_ATTACK_INTERVAL_TICKS;
                this.eniac.swing(InteractionHand.MAIN_HAND);
                this.eniac.doHurtTarget(target);
            }
        }

        @Override
        protected double getAttackReachSqr(LivingEntity target) {
            return this.eniac.getPlayerReachSqr(target);
        }
    }

    /**
     * When ENIAC's aggro target sits out of melee reach and at least {@link #LEAP_TRIGGER_HEIGHT} blocks
     * overhead (e.g. perched on a ledge it otherwise can't touch), ENIAC leaps up at it and, on contact,
     * lands a melee hit carrying a Knockback II-equivalent punch (each knockback level = 0.5 strength).
     */
    private static final class LeapAtHighTargetGoal extends Goal {
        private final EniacEntity eniac;
        private LivingEntity target;
        private int airTicks;
        private boolean struck;

        private LeapAtHighTargetGoal(EniacEntity eniac) {
            this.eniac = eniac;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.eniac.isReviving() || this.eniac.isSkillARunning() || this.eniac.isSkillBRunning()
                    || this.eniac.isSkillCRunning()
                    || this.eniac.leapCooldown > 0 || !this.eniac.onGround()) {
                return false;
            }
            LivingEntity candidate = this.eniac.getTarget();
            if (candidate == null || !candidate.isAlive() || !this.eniac.hasLineOfSight(candidate)) {
                return false;
            }
            if (candidate.getY() - this.eniac.getY() < LEAP_TRIGGER_HEIGHT) {
                return false;
            }
            // Only leap when a normal swing genuinely can't connect.
            if (this.eniac.distanceToSqr(candidate) <= this.eniac.getPlayerReachSqr(candidate)) {
                return false;
            }
            double dx = candidate.getX() - this.eniac.getX();
            double dz = candidate.getZ() - this.eniac.getZ();
            if (dx * dx + dz * dz > LEAP_MAX_HORIZONTAL * LEAP_MAX_HORIZONTAL) {
                return false;
            }
            this.target = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.struck
                    && this.airTicks < LEAP_MAX_AIR_TICKS
                    && (this.airTicks <= 2 || !this.eniac.onGround())
                    && this.target != null && this.target.isAlive()
                    && !this.eniac.isReviving();
        }

        @Override
        public void start() {
            this.eniac.leapCooldown = LEAP_COOLDOWN_TICKS;
            this.struck = false;
            this.airTicks = 0;
            this.eniac.getNavigation().stop();
            this.eniac.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            this.eniac.triggerAnim("actions", "skillb_leap");
            this.launch();
        }

        /** Aims an upward leap tall enough to reach the target's height (so it enters melee range), plus horizontal velocity toward it. */
        private void launch() {
            double dx = this.target.getX() - this.eniac.getX();
            double dz = this.target.getZ() - this.eniac.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            // Peak the hop a little above the target so ENIAC's melee reach connects on the way up; solve
            // the vanilla ballistic (h ~= v^2 / 0.16) for the launch velocity, then clamp to sane bounds.
            double heightDiff = Math.max(0.0D, this.target.getY() - this.eniac.getY());
            double desiredPeak = heightDiff + 1.0D;
            double vy = Math.sqrt(0.16D * desiredPeak) * 1.12D;
            vy = Math.max(PLAYER_JUMP_VELOCITY, Math.min(vy, LEAP_MAX_LAUNCH_VELOCITY));
            double vh = horiz > 1.0E-4D ? Math.min(horiz * 0.25D, 0.6D) : 0.0D;
            double vx = horiz > 1.0E-4D ? dx / horiz * vh : 0.0D;
            double vz = horiz > 1.0E-4D ? dz / horiz * vh : 0.0D;
            this.eniac.setDeltaMovement(vx, vy, vz);
            this.eniac.hasImpulse = true;
            this.eniac.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
        }

        @Override
        public void tick() {
            this.airTicks++;
            if (this.target == null) {
                return;
            }
            this.eniac.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            if (!this.struck && this.eniac.distanceToSqr(this.target) <= this.eniac.getPlayerReachSqr(this.target)) {
                this.struck = true;
                this.eniac.triggerAnim("actions", "attack");
                if (this.eniac.doHurtTarget(this.target)) {
                    this.target.knockback(LEAP_KNOCKBACK,
                            this.eniac.getX() - this.target.getX(),
                            this.eniac.getZ() - this.target.getZ());
                    this.target.hurtMarked = true;
                }
            }
        }

        @Override
        public void stop() {
            this.target = null;
            this.struck = false;
            this.airTicks = 0;
        }
    }

    /**
     * Phase-1 skillA: 30s cooldown. On cast ENIAC gains a transient Speed-II-equivalent movement modifier,
     * dashes to the lowest-armour hostile target playing skillArun, then performs skillAattack which flings
     * the target upward and leaves it airborne. If it can't reach melee range within 10s it breaks off the
     * chase and finishes with the ranged trident throw instead.
     */
    private static final class SkillAGoal extends Goal {
        private static final int PHASE_APPROACH = 0;
        private static final int PHASE_STRIKE = 1;
        private static final int PHASE_BOW = 2;
        private static final int PHASE_DONE = 3;

        private final EniacEntity eniac;
        private LivingEntity target;
        private int phase;
        private int timer;

        private SkillAGoal(EniacEntity eniac) {
            this.eniac = eniac;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.eniac.getPhase() != 1 || this.eniac.isReviving() || this.eniac.skillACooldown > 0) {
                return false;
            }
            this.target = this.eniac.findLowestArmorTarget();
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.phase != PHASE_DONE && this.target != null && this.target.isAlive();
        }

        @Override
        public void start() {
            this.eniac.skillACooldown = SKILL_A_COOLDOWN_TICKS;
            this.eniac.setTarget(this.target);
            this.eniac.applySkillASpeed();
            this.eniac.equipSkillAAxe();
            this.eniac.setSkillARunning(true);
            this.eniac.getNavigation().moveTo(this.target, RUN_SPEED_MODIFIER);
            this.phase = PHASE_APPROACH;
            this.timer = 0;
        }

        @Override
        public void tick() {
            if (this.target == null) {
                this.phase = PHASE_DONE;
                return;
            }
            this.eniac.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            switch (this.phase) {
                case PHASE_APPROACH -> {
                    this.eniac.getNavigation().moveTo(this.target, RUN_SPEED_MODIFIER);
                    if (this.eniac.distanceToSqr(this.target) <= this.eniac.getPlayerReachSqr(this.target)) {
                        this.eniac.setSkillARunning(false);
                        this.eniac.getNavigation().stop();
                        this.eniac.triggerAnim("actions", "skillAattack");
                        // Fling the target the instant the swing starts, not at the end of the animation.
                        this.eniac.performSkillALaunch(this.target);
                        this.timer = SKILL_A_STRIKE_DELAY_TICKS;
                        this.phase = PHASE_STRIKE;
                    } else if (++this.timer >= SKILL_A_CHASE_TIMEOUT_TICKS) {
                        // Never closed to melee within 10s: give up the chase and throw the trident instead.
                        this.beginBowThrow();
                    }
                }
                case PHASE_STRIKE -> {
                    this.freeze();
                    if (--this.timer <= 0) {
                        this.beginBowThrow();
                    }
                }
                case PHASE_BOW -> {
                    this.freeze();
                    if (--this.timer <= 0) {
                        this.phase = PHASE_DONE;
                    }
                }
                default -> {
                }
            }
        }

        /** Ends the chase/strike beat: swaps to the shock bow and releases the trident as the throw begins. */
        private void beginBowThrow() {
            this.eniac.setSkillARunning(false);
            this.eniac.getNavigation().stop();
            this.eniac.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PAMA_BOW.get()));
            this.eniac.triggerAnim("actions", "attack");
            // Trident leaves the hand the instant the throw animation starts, not when it finishes.
            this.eniac.fireSkillATrident(this.target);
            this.timer = SKILL_A_BOW_DELAY_TICKS;
            this.phase = PHASE_BOW;
        }

        private void freeze() {
            this.eniac.setDeltaMovement(0.0D, Math.min(this.eniac.getDeltaMovement().y, 0.0D), 0.0D);
        }

        @Override
        public void stop() {
            this.eniac.setSkillARunning(false);
            this.eniac.getNavigation().stop();
            this.eniac.removeSkillASpeed();
            // Return to the base loadout (pama_sword) once the skill ends.
            this.eniac.equipBaseWeapon();
            this.target = null;
            this.phase = PHASE_DONE;
            this.timer = 0;
        }
    }

    /**
     * Phase-1 skillB: 30s cooldown, only when the target is beyond {@link #SKILL_B_MIN_RANGE}. ENIAC hurls
     * three ender pearls in sequence; each pearl teleports it beside the target for an in-place three-slash
     * combo. Each connecting slash stacks Slowness; the first two slashes never knock back and the third
     * settles the accumulated Knockback II x3. Whiff any slash and ENIAC breaks off the combo, switches to
     * the crossbow, and fires a spectral arrow followed by two coil arrows before moving to the next pearl.
     */
    private static final class SkillBGoal extends Goal {
        private static final int PHASE_THROW = 0;
        private static final int PHASE_WAIT_TELEPORT = 1;
        private static final int PHASE_SLASH_WINDUP = 2;
        private static final int PHASE_SLASH_STRIKE = 3;
        private static final int PHASE_SLASH_RECOVER = 4;
        private static final int PHASE_INTERCEPT = 5;
        private static final int PHASE_ROUND_END = 6;
        private static final int PHASE_DONE = 7;

        private final EniacEntity eniac;
        private LivingEntity target;
        private PamaEnderPearlEntity pearl;
        private int pearlsRemaining;
        private int slashIndex;
        private int interceptShot;
        private int phase;
        private int timer;

        private SkillBGoal(EniacEntity eniac) {
            this.eniac = eniac;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.eniac.getPhase() != 1 || this.eniac.isReviving()
                    || this.eniac.isSkillARunning() || this.eniac.skillBCooldown > 0) {
                return false;
            }
            LivingEntity candidate = this.eniac.findLowestArmorTarget();
            if (candidate == null) {
                return false;
            }
            if (this.eniac.distanceToSqr(candidate) < SKILL_B_MIN_RANGE * SKILL_B_MIN_RANGE) {
                return false;
            }
            this.target = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.phase != PHASE_DONE && this.target != null && this.target.isAlive();
        }

        @Override
        public void start() {
            this.eniac.skillBCooldown = SKILL_B_COOLDOWN_TICKS;
            this.eniac.setTarget(this.target);
            this.eniac.setSkillBRunning(true);
            this.eniac.getNavigation().stop();
            this.pearlsRemaining = SKILL_B_PEARL_COUNT;
            this.slashIndex = 0;
            this.interceptShot = 0;
            this.phase = PHASE_THROW;
            this.timer = 0;
        }

        @Override
        public void tick() {
            if (this.target == null || !this.target.isAlive()) {
                this.phase = PHASE_DONE;
                return;
            }
            this.freeze();
            this.eniac.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            switch (this.phase) {
                case PHASE_THROW -> {
                    this.eniac.equipBaseWeapon();
                    this.eniac.triggerAnim("actions", "skillb_leap");
                    this.pearl = this.eniac.throwSkillBPearl(this.target);
                    this.timer = SKILL_B_PEARL_TIMEOUT_TICKS;
                    this.phase = PHASE_WAIT_TELEPORT;
                }
                case PHASE_WAIT_TELEPORT -> {
                    // The pearl teleports ENIAC and discards itself on impact; a timeout covers a lost pearl.
                    if (this.pearl == null || this.pearl.isRemoved() || --this.timer <= 0) {
                        this.pearl = null;
                        this.slashIndex = 0;
                        this.phase = PHASE_SLASH_WINDUP;
                    }
                }
                case PHASE_SLASH_WINDUP -> {
                    this.eniac.triggerAnim("actions", "skillb_slash");
                    this.timer = SKILL_B_SLASH_STRIKE_TICKS;
                    this.phase = PHASE_SLASH_STRIKE;
                }
                case PHASE_SLASH_STRIKE -> {
                    if (--this.timer <= 0) {
                        boolean knockbackSlash = this.slashIndex == SKILL_B_SLASHES_PER_ROUND - 1;
                        boolean hit = this.eniac.resolveSkillBSlash(this.target, knockbackSlash);
                        if (!hit) {
                            // A whiff breaks the combo: switch to the crossbow and intercept.
                            this.eniac.equipSkillBCrossbow();
                            this.eniac.triggerAnim("actions", "skillb_shoot");
                            this.interceptShot = 0;
                            this.timer = 0;
                            this.phase = PHASE_INTERCEPT;
                        } else {
                            this.timer = SKILL_B_SLASH_RECOVER_TICKS;
                            this.phase = PHASE_SLASH_RECOVER;
                        }
                    }
                }
                case PHASE_SLASH_RECOVER -> {
                    if (--this.timer <= 0) {
                        this.slashIndex++;
                        this.phase = this.slashIndex < SKILL_B_SLASHES_PER_ROUND
                                ? PHASE_SLASH_WINDUP
                                : PHASE_ROUND_END;
                    }
                }
                case PHASE_INTERCEPT -> {
                    if (--this.timer <= 0) {
                        this.eniac.fireSkillBInterceptShot(this.interceptShot, this.target);
                        this.interceptShot++;
                        if (this.interceptShot >= SKILL_B_INTERCEPT_SHOTS) {
                            this.phase = PHASE_ROUND_END;
                        } else {
                            this.timer = SKILL_B_INTERCEPT_INTERVAL_TICKS;
                        }
                    }
                }
                case PHASE_ROUND_END -> {
                    this.pearlsRemaining--;
                    if (this.pearlsRemaining > 0) {
                        this.slashIndex = 0;
                        this.phase = PHASE_THROW;
                    } else {
                        this.phase = PHASE_DONE;
                    }
                }
                default -> {
                }
            }
        }

        private void freeze() {
            this.eniac.setDeltaMovement(0.0D, Math.min(this.eniac.getDeltaMovement().y, 0.0D), 0.0D);
        }

        @Override
        public void stop() {
            this.eniac.setSkillBRunning(false);
            this.eniac.getNavigation().stop();
            this.eniac.equipBaseWeapon();
            this.target = null;
            this.pearl = null;
            this.pearlsRemaining = 0;
            this.slashIndex = 0;
            this.interceptShot = 0;
            this.phase = PHASE_DONE;
            this.timer = 0;
        }
    }

    /**
     * Phase-1 skillC: 30s cooldown. ENIAC equips the powered polearm, plays skillC1 while standing still to
     * lock onto the highest-health enemy, then plays walk2 and charges. Each of the five dashes is a
     * velocity-driven charge that accelerates from ENIAC's run pace up to {@link #SKILL_C_CHARGE_MAX_MULTIPLIER}x,
     * deals powered-polearm charge-style (speed-scaled) damage plus directional knockback on contact, then
     * charges THROUGH the target and decelerates rather than stopping — leaving run-up room for the next dash.
     * If a strike is caught on a shield it halts, swaps to an axe to break the shield, then resumes. After the
     * fifth dash the skill ends.
     */
    private static final class SkillCGoal extends Goal {
        private static final int PHASE_LOCK = 0;
        private static final int PHASE_CHARGE = 1;
        private static final int PHASE_SHIELD_BREAK = 2;
        private static final int PHASE_DONE = 3;

        private final EniacEntity eniac;
        private LivingEntity target;
        private int dashesRemaining;
        private int phase;
        private int timer;
        // Horizontal charge heading. It tracks the target while closing in, then freezes on the hit so ENIAC
        // charges straight through instead of curving back onto a target it just knocked aside.
        private double chargeDirX;
        private double chargeDirZ;
        private double chargeMultiplier;
        private boolean struck;

        private SkillCGoal(EniacEntity eniac) {
            this.eniac = eniac;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.eniac.getPhase() != 1 || this.eniac.isReviving()
                    || this.eniac.isSkillARunning() || this.eniac.isSkillBRunning()
                    || this.eniac.skillCCooldown > 0) {
                return false;
            }
            this.target = this.eniac.findHighestHealthTarget();
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.phase != PHASE_DONE && this.dashesRemaining > 0;
        }

        @Override
        public void start() {
            this.eniac.skillCCooldown = SKILL_C_COOLDOWN_TICKS;
            this.eniac.setSkillCRunning(true);
            this.eniac.equipPoweredPolearm();
            this.dashesRemaining = SKILL_C_DASH_COUNT;
            this.beginLock();
        }

        /** Start a dash: re-pick the highest-health target and hold still through the skillC1 windup. */
        private void beginLock() {
            this.target = this.eniac.findHighestHealthTarget();
            this.eniac.getNavigation().stop();
            this.phase = PHASE_LOCK;
            this.timer = SKILL_C_LOCK_TICKS;
            this.struck = false;
            this.chargeMultiplier = 1.0D;
        }

        @Override
        public void tick() {
            if (this.target == null || !this.target.isAlive()) {
                this.target = this.eniac.findHighestHealthTarget();
                if (this.target == null) {
                    this.phase = PHASE_DONE;
                    return;
                }
            }
            this.eniac.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            switch (this.phase) {
                case PHASE_LOCK -> {
                    this.freeze();
                    this.eniac.setTarget(this.target);
                    if (--this.timer <= 0) {
                        this.timer = SKILL_C_APPROACH_TIMEOUT_TICKS;
                        this.phase = PHASE_CHARGE;
                    }
                }
                case PHASE_CHARGE -> this.charge();
                case PHASE_SHIELD_BREAK -> {
                    this.freeze();
                    if (--this.timer <= 0) {
                        this.eniac.breakTargetShield(this.target);
                        this.eniac.equipPoweredPolearm();
                        this.finishDash();
                    }
                }
                default -> {
                }
            }
        }

        /**
         * Velocity-driven charge. Before the hit ENIAC steers toward the target and accelerates up to
         * {@link #SKILL_C_CHARGE_MAX_MULTIPLIER}x its run pace; on contact it deals charge-style damage and
         * keeps its heading; afterwards it decelerates and coasts through until the charge dies down, then
         * re-locks for the next dash. A shield-blocked hit halts it into the shield-break phase instead.
         */
        private void charge() {
            if (!this.struck) {
                double dx = this.target.getX() - this.eniac.getX();
                double dz = this.target.getZ() - this.eniac.getZ();
                double horiz = Math.sqrt(dx * dx + dz * dz);
                if (horiz > 1.0E-4D) {
                    this.chargeDirX = dx / horiz;
                    this.chargeDirZ = dz / horiz;
                }
                this.chargeMultiplier = Math.min(SKILL_C_CHARGE_MAX_MULTIPLIER,
                        this.chargeMultiplier + SKILL_C_CHARGE_ACCEL_PER_TICK);
                this.drive();
                if (this.eniac.distanceToSqr(this.target) <= this.eniac.getPlayerReachSqr(this.target)) {
                    this.strike();
                } else if (this.eniac.horizontalCollision || --this.timer <= 0) {
                    // Wall or timeout before reaching the target; spend this dash and re-lock.
                    this.finishDash();
                }
                return;
            }

            // Follow-through: keep the locked heading and bleed off speed so ENIAC coasts past the target.
            this.chargeMultiplier -= SKILL_C_CHARGE_DECEL_PER_TICK;
            if (this.chargeMultiplier <= SKILL_C_FOLLOW_THROUGH_MIN_MULTIPLIER || this.eniac.horizontalCollision) {
                this.finishDash();
                return;
            }
            this.drive();
        }

        /** Push ENIAC along the locked charge heading at the current multiple of its run pace. */
        private void drive() {
            double speed = SKILL_C_CHARGE_BASE_SPEED * this.chargeMultiplier;
            this.eniac.setDeltaMovement(this.chargeDirX * speed,
                    this.eniac.getDeltaMovement().y, this.chargeDirZ * speed);
            this.eniac.hasImpulse = true;
        }

        /** Land the charge hit (speed-scaled damage + directional knockback), or halt to break a raised shield. */
        private void strike() {
            DamageSource source = this.eniac.damageSources().mobAttack(this.eniac);
            if (this.target instanceof Player player && player.isBlocking()
                    && player.isDamageSourceBlocked(source)) {
                this.eniac.getNavigation().stop();
                this.freeze();
                this.eniac.triggerAnim("actions", "skillAattack");
                this.eniac.equipSkillCAxe();
                this.timer = SKILL_C_SHIELD_BREAK_TICKS;
                this.phase = PHASE_SHIELD_BREAK;
                return;
            }

            // Mirror RoyalPolearmEvents#applyChargeHit: damage scales with charge speed; knockback runs along the heading.
            Vec3 delta = this.eniac.getDeltaMovement();
            Vec3 targetDelta = this.target.getDeltaMovement();
            double attackerSpeed = delta.x * this.chargeDirX + delta.z * this.chargeDirZ;
            double relativeSpeed = Math.abs((delta.x - targetDelta.x) * this.chargeDirX
                    + (delta.z - targetDelta.z) * this.chargeDirZ);
            float damage = Math.max(1.0F, (float) Math.ceil(Math.max(attackerSpeed, relativeSpeed) * 20.0D));
            this.eniac.triggerAnim("actions", "attack");
            boolean harmed = this.target.hurt(source, damage);
            if (harmed && attackerSpeed > SKILL_C_CHARGE_KNOCKBACK_SPEED) {
                this.target.knockback(SKILL_C_CHARGE_KNOCKBACK_STRENGTH, -this.chargeDirX, -this.chargeDirZ);
                this.target.hurtMarked = true;
            }
            // Do not stop: keep the heading and let the follow-through coast ENIAC through the target.
            this.struck = true;
        }

        private void finishDash() {
            this.dashesRemaining--;
            if (this.dashesRemaining > 0) {
                this.beginLock();
            } else {
                this.phase = PHASE_DONE;
            }
        }

        private void freeze() {
            this.eniac.setDeltaMovement(0.0D, Math.min(this.eniac.getDeltaMovement().y, 0.0D), 0.0D);
        }

        @Override
        public void stop() {
            this.eniac.setSkillCRunning(false);
            this.eniac.getNavigation().stop();
            this.eniac.equipBaseWeapon();
            this.target = null;
            this.dashesRemaining = 0;
            this.phase = PHASE_DONE;
            this.timer = 0;
            this.struck = false;
        }
    }
}
