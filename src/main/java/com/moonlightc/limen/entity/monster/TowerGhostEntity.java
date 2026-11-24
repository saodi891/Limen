package com.moonlightc.limen.entity.monster;

import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.entity.projectile.TowerGhostBlueFlameEntity;
import com.moonlightc.limen.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TowerGhostEntity extends Monster implements GeoEntity {
    public static final int CAST_IDLE = 0;
    public static final int CAST_BLUE_FIRE = 1;
    public static final int CAST_POISON = 2;

    private static final String TAG_TELEPORT_COOLDOWN = "TowerGhostTeleportCooldown";
    private static final String TAG_BLUE_FIRE_COOLDOWN = "TowerGhostBlueFireCooldown";
    private static final String TAG_POISON_COOLDOWN = "TowerGhostPoisonCooldown";
    private static final String TAG_SPELL_GLOBAL_COOLDOWN = "TowerGhostSpellGlobalCooldown";
    private static final String TAG_CAST_STATE = "TowerGhostCastState";
    private static final String TAG_CAST_TICKS = "TowerGhostCastTicks";
    private static final String TAG_TELEPORT_IN_DELAY = "TowerGhostTeleportInDelay";

    private static final EntityDataAccessor<Integer> DATA_CAST_STATE =
            SynchedEntityData.defineId(TowerGhostEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tower_wraith.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.tower_wraith.walk");
    private static final RawAnimation BLUE_FIRE_CAST =
            RawAnimation.begin().thenPlay("animation.tower_wraith.blue_fire_cast");
    private static final RawAnimation POISON_CAST =
            RawAnimation.begin().thenPlay("animation.tower_wraith.poison_cloud_cast");
    private static final RawAnimation TELEPORT_OUT =
            RawAnimation.begin().thenPlay("animation.tower_wraith.teleport_out");
    private static final RawAnimation TELEPORT_IN =
            RawAnimation.begin().thenPlay("animation.tower_wraith.teleport_in");

    private static final int CAST_WINDUP_TICKS = 20;
    private static final int TELEPORT_COOLDOWN_TICKS = 5 * 20;
    private static final int LOW_HEALTH_TELEPORT_COOLDOWN_TICKS = 4 * 20;
    private static final int SPELL_COOLDOWN_TICKS = 6 * 20;
    private static final int SPELL_GLOBAL_COOLDOWN_TICKS = 3 * 20;
    private static final int INTERRUPT_DAMAGE_THRESHOLD = 5;
    private static final int TELEPORT_IN_DELAY_TICKS = 5;
    private static final double TARGET_RANGE = 20.0D;
    private static final double SPELL_RANGE = 15.0D;
    private static final double TELEPORT_NEAR_RANGE = 4.0D;
    private static final double LOW_HEALTH_TELEPORT_NEAR_RANGE = 6.0D;
    private static final double TELEPORT_MIN_TARGET_DISTANCE = 10.0D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int teleportCooldownTicks;
    private int blueFireCooldownTicks;
    private int poisonCooldownTicks;
    private int spellGlobalCooldownTicks;
    private int castTicks;
    private int teleportInDelayTicks;

    public TowerGhostEntity(EntityType<? extends TowerGhostEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FOLLOW_RANGE, TARGET_RANGE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CAST_STATE, CAST_IDLE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2,
                new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
        this.targetSelector.addGoal(3,
                new NearestAttackableTargetGoal<>(this, Villager.class, 10, false, false, null));
        this.targetSelector.addGoal(3,
                new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, false, false, null));
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        return entity == this || entity.getType().is(LimenEntityTypeTags.GHOSTS) || super.isAlliedTo(entity);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isResistedDamage(source)) {
            amount *= 0.33F;
        }

        float healthBefore = this.getHealth();
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive()
                && this.getCastState() != CAST_IDLE
                && healthBefore - this.getHealth() > INTERRUPT_DAMAGE_THRESHOLD) {
            this.interruptCastAndTeleport();
        }
        return hurt;
    }

    private static boolean isResistedDamage(DamageSource source) {
        return source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.THORNS)
                || source.is(DamageTypes.SONIC_BOOM);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            this.tickCombat();
        }
    }

    private void tickCombat() {
        if (this.teleportInDelayTicks > 0 && --this.teleportInDelayTicks == 0) {
            this.triggerAnim("actions", "teleport_in");
        }
        this.teleportCooldownTicks = Math.max(0, this.teleportCooldownTicks - 1);
        this.blueFireCooldownTicks = Math.max(0, this.blueFireCooldownTicks - 1);
        this.poisonCooldownTicks = Math.max(0, this.poisonCooldownTicks - 1);
        this.spellGlobalCooldownTicks = Math.max(0, this.spellGlobalCooldownTicks - 1);

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.distanceToSqr(target) > TARGET_RANGE * TARGET_RANGE) {
            this.setTarget(null);
            target = null;
        }

        if (this.getCastState() != CAST_IDLE) {
            this.tickCast(target);
            return;
        }
        if (target == null) {
            return;
        }

        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boolean lowHealth = this.getHealth() <= this.getMaxHealth() * 0.5F;
        double distanceSqr = this.distanceToSqr(target);
        double nearRange = lowHealth ? LOW_HEALTH_TELEPORT_NEAR_RANGE : TELEPORT_NEAR_RANGE;
        boolean shouldTeleport = lowHealth || distanceSqr <= nearRange * nearRange;
        if (shouldTeleport && this.teleportCooldownTicks <= 0 && this.teleportAwayFrom(target, false)) {
            this.teleportCooldownTicks = lowHealth
                    ? LOW_HEALTH_TELEPORT_COOLDOWN_TICKS : TELEPORT_COOLDOWN_TICKS;
            return;
        }

        if (distanceSqr > SPELL_RANGE * SPELL_RANGE) {
            this.getNavigation().moveTo(target, 1.0D);
            return;
        }

        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        if (this.spellGlobalCooldownTicks > 0
                || this.blueFireCooldownTicks > 0 && this.poisonCooldownTicks > 0) {
            return;
        }

        int skill = this.chooseNextSpell();
        this.beginCast(skill);
    }

    private int chooseNextSpell() {
        if (this.blueFireCooldownTicks <= 0) {
            return CAST_BLUE_FIRE;
        }
        return CAST_POISON;
    }

    private void beginCast(int skill) {
        this.castTicks = CAST_WINDUP_TICKS;
        this.entityData.set(DATA_CAST_STATE, skill);
        this.getNavigation().stop();
        if (skill == CAST_BLUE_FIRE) {
            this.blueFireCooldownTicks = SPELL_COOLDOWN_TICKS;
            this.triggerAnim("actions", "blue_fire_cast");
        } else {
            this.poisonCooldownTicks = SPELL_COOLDOWN_TICKS;
            this.triggerAnim("actions", "poison_cloud_cast");
        }
    }

    private void tickCast(@Nullable LivingEntity target) {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        if (target != null && target.isAlive()) {
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (--this.castTicks > 0) {
            return;
        }

        int castState = this.getCastState();
        this.entityData.set(DATA_CAST_STATE, CAST_IDLE);
        this.spellGlobalCooldownTicks = SPELL_GLOBAL_COOLDOWN_TICKS;
        if (target == null || !target.isAlive() || this.distanceToSqr(target) > SPELL_RANGE * SPELL_RANGE) {
            return;
        }
        if (castState == CAST_BLUE_FIRE) {
            this.releaseBlueFire(target);
        } else if (castState == CAST_POISON) {
            this.releasePoisonCloud(target);
        }
    }

    private void releaseBlueFire(LivingEntity target) {
        Level level = this.level();
        BlockPos center = target.blockPosition();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z > 6) {
                    continue;
                }
                BlockPos ground = findGround(level, center.offset(x, 0, z));
                if (ground == null) {
                    continue;
                }
                TowerGhostBlueFlameEntity flame = new TowerGhostBlueFlameEntity(
                        ModEntities.SOUL_FLAME.get(), level, this,
                        ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 0.5D);
                level.addFreshEntity(flame);
            }
        }
    }

    @Nullable
    private static BlockPos findGround(Level level, BlockPos around) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(around.getX(), around.getY(), around.getZ());
        for (int y = around.getY(); y >= level.getMinBuildHeight(); y--) {
            cursor.set(around.getX(), y, around.getZ());
            BlockPos supportPos = cursor.below();
            BlockState support = level.getBlockState(supportPos);
            if (support.isFaceSturdy(level, supportPos, net.minecraft.core.Direction.UP)
                    && level.isEmptyBlock(cursor) && level.isEmptyBlock(cursor.above())) {
                return supportPos.immutable();
            }
        }
        return null;
    }

    private void releasePoisonCloud(LivingEntity target) {
        net.minecraft.world.entity.AreaEffectCloud cloud =
                new net.minecraft.world.entity.AreaEffectCloud(this.level(), target.getX(), target.getY(), target.getZ());
        cloud.setOwner(this);
        cloud.setPotion(net.minecraft.world.item.alchemy.Potions.STRONG_POISON);
        cloud.setRadius(3.0F);
        cloud.setRadiusOnUse(-0.5F);
        cloud.setWaitTime(10);
        cloud.setDuration(600);
        cloud.setRadiusPerTick(-3.0F / 600.0F);
        this.level().addFreshEntity(cloud);
    }

    private void interruptCastAndTeleport() {
        int interruptedSkill = this.getCastState();
        LivingEntity target = this.getTarget();
        if (interruptedSkill == CAST_BLUE_FIRE) {
            this.blueFireCooldownTicks = SPELL_COOLDOWN_TICKS;
        } else if (interruptedSkill == CAST_POISON) {
            this.poisonCooldownTicks = SPELL_COOLDOWN_TICKS;
        }
        this.castTicks = 0;
        this.entityData.set(DATA_CAST_STATE, CAST_IDLE);
        this.getNavigation().stop();
        if (target != null && target.isAlive() && this.teleportAwayFrom(target, true)) {
            this.teleportCooldownTicks = this.getHealth() <= this.getMaxHealth() * 0.5F
                    ? LOW_HEALTH_TELEPORT_COOLDOWN_TICKS : TELEPORT_COOLDOWN_TICKS;
        }
    }

    private boolean teleportAwayFrom(LivingEntity target, boolean forced) {
        RandomSource random = this.getRandom();
        BlockPos origin = this.blockPosition();
        for (int attempt = 0; attempt < 32; attempt++) {
            int offsetX = random.nextInt(33) - 16;
            int offsetZ = random.nextInt(33) - 16;
            if (offsetX * offsetX + offsetZ * offsetZ < 25) {
                continue;
            }
            int x = origin.getX() + offsetX;
            int z = origin.getZ() + offsetZ;
            int y = Mth.clamp(origin.getY() + random.nextInt(17) - 8,
                    this.level().getMinBuildHeight(), this.level().getMaxBuildHeight() - 2);
            BlockPos candidate = findTeleportLanding(this.level(), x, y, z);
            if (candidate == null) {
                continue;
            }
            double destinationX = candidate.getX() + 0.5D;
            double destinationZ = candidate.getZ() + 0.5D;
            if (target.distanceToSqr(destinationX, candidate.getY(), destinationZ)
                    <= TELEPORT_MIN_TARGET_DISTANCE * TELEPORT_MIN_TARGET_DISTANCE) {
                continue;
            }

            this.triggerAnim("actions", "teleport_out");
            this.teleportInDelayTicks = TELEPORT_IN_DELAY_TICKS;
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
            this.teleportTo(destinationX, candidate.getY(), destinationZ);
            this.setDeltaMovement(Vec3.ZERO);
            this.hasImpulse = true;
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
            return true;
        }

        if (forced) {
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
        }
        return false;
    }

    @Nullable
    private static BlockPos findTeleportLanding(Level level, int x, int startY, int z) {
        for (int delta = 0; delta <= 8; delta++) {
            for (int sign : new int[] {1, -1}) {
                int y = startY + delta * sign;
                if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 1) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                BlockState floor = level.getBlockState(pos.below());
                if (floor.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                        && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                    return pos;
                }
            }
        }
        return null;
    }

    public boolean isHostileTo(LivingEntity target) {
        return target == this.getTarget()
                || target == this.getLastHurtByMob()
                || target instanceof Mob mob && (mob.getTarget() == this || mob.getLastHurtByMob() == this);
    }

    public int getCastState() {
        return this.entityData.get(DATA_CAST_STATE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_TELEPORT_COOLDOWN, this.teleportCooldownTicks);
        tag.putInt(TAG_BLUE_FIRE_COOLDOWN, this.blueFireCooldownTicks);
        tag.putInt(TAG_POISON_COOLDOWN, this.poisonCooldownTicks);
        tag.putInt(TAG_SPELL_GLOBAL_COOLDOWN, this.spellGlobalCooldownTicks);
        tag.putInt(TAG_CAST_STATE, this.getCastState());
        tag.putInt(TAG_CAST_TICKS, this.castTicks);
        tag.putInt(TAG_TELEPORT_IN_DELAY, this.teleportInDelayTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.teleportCooldownTicks = Math.max(0, tag.getInt(TAG_TELEPORT_COOLDOWN));
        this.blueFireCooldownTicks = Math.max(0, tag.getInt(TAG_BLUE_FIRE_COOLDOWN));
        this.poisonCooldownTicks = Math.max(0, tag.getInt(TAG_POISON_COOLDOWN));
        this.spellGlobalCooldownTicks = Math.max(0, tag.getInt(TAG_SPELL_GLOBAL_COOLDOWN));
        int castState = tag.getInt(TAG_CAST_STATE);
        this.entityData.set(DATA_CAST_STATE, castState == CAST_BLUE_FIRE || castState == CAST_POISON
                ? castState : CAST_IDLE);
        this.castTicks = Math.max(0, tag.getInt(TAG_CAST_TICKS));
        this.teleportInDelayTicks = Math.max(0, tag.getInt(TAG_TELEPORT_IN_DELAY));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "actions", 0, state -> PlayState.STOP)
                .triggerableAnim("blue_fire_cast", BLUE_FIRE_CAST)
                .triggerableAnim("poison_cloud_cast", POISON_CAST)
                .triggerableAnim("teleport_out", TELEPORT_OUT)
                .triggerableAnim("teleport_in", TELEPORT_IN));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
