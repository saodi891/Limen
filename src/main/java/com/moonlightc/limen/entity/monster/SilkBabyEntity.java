package com.moonlightc.limen.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public class SilkBabyEntity extends Spider implements GeoEntity {
    private static final double MOVEMENT_SPEED = 0.375D;
    private static final int FIRE_REFRESH_TICKS = 20;
    private static final int ATTACK_FIRE_SECONDS = 5;
    private static final int WATER_SEARCH_COOLDOWN_TICKS = 20;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.silk_baby.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.silk_baby.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.silk_baby.run");
    private static final RawAnimation BURNING = RawAnimation.begin().thenLoop("animation.silk_baby.burning");
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlay("animation.silk_baby.jump");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlay("animation.silk_baby.land");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.silk_baby.death");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int jumpCooldown;
    private int landAnimationTicks;

    public SilkBabyEntity(EntityType<? extends Spider> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.5D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 5.5D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SeekWaterGoal(this));
        this.goalSelector.addGoal(2, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(4, new RabbitLikeJumpGoal(this));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData, @Nullable net.minecraft.nbt.CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, dataTag);
        this.setAttackDamage(difficulty.getDifficulty());
        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            if (this.isInWaterOrBubble()) {
                this.clearFire();
            } else if (this.isOnFire()) {
                this.setRemainingFireTicks(Math.max(this.getRemainingFireTicks(), FIRE_REFRESH_TICKS));
            }
            if (this.jumpCooldown > 0) {
                this.jumpCooldown--;
            }
        }

        if (!this.onGround()) {
            this.landAnimationTicks = 0;
        } else if (this.landAnimationTicks > 0) {
            this.landAnimationTicks--;
        }
    }

    @Override
    protected void checkFallDamage(double yMovement, boolean onGround, net.minecraft.world.level.block.state.BlockState state,
                                   BlockPos pos) {
        boolean landed = onGround && this.fallDistance > 0.0F;
        super.checkFallDamage(yMovement, onGround, state, pos);
        if (landed) {
            this.landAnimationTicks = 5;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean wasBurning = this.isOnFire();
        boolean hurt = super.doHurtTarget(target);
        if (hurt && wasBurning && target instanceof LivingEntity livingTarget && !this.level().isClientSide) {
            livingTarget.setSecondsOnFire(ATTACK_FIRE_SECONDS);
        }
        return hurt;
    }

    private void setAttackDamage(Difficulty difficulty) {
        if (this.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            return;
        }
        double damage = switch (difficulty) {
            case HARD -> 6.0D;
            case NORMAL -> 5.5D;
            default -> 5.0D;
        };
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
    }

    private boolean shouldJump() {
        LivingEntity target = this.getTarget();
        return this.onGround()
                && this.jumpCooldown <= 0
                && (this.horizontalCollision || target != null && target.getY() > this.getY() + 0.5D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "silk_baby", 5, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (this.isOnFire()) {
                return state.setAndContinue(BURNING);
            }
            if (this.landAnimationTicks > 0) {
                return state.setAndContinue(LAND);
            }
            if (!this.onGround()) {
                return state.setAndContinue(JUMP);
            }
            if (state.isMoving()) {
                return state.setAndContinue(this.getTarget() != null ? RUN : WALK);
            }
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "actions", 0,
                state -> software.bernie.geckolib.core.object.PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("animation.silk_baby.attack"))
                .triggerableAnim("hurt", RawAnimation.begin().thenPlay("animation.silk_baby.hurt")));
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.isDeadOrDying()) {
            this.triggerAnim("actions", "hurt");
        }
        return hurt;
    }

    @Override
    public void swing(net.minecraft.world.InteractionHand hand) {
        super.swing(hand);
        this.triggerAnim("actions", "attack");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    private static final class RabbitLikeJumpGoal extends Goal {
        private final SilkBabyEntity mob;

        private RabbitLikeJumpGoal(SilkBabyEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.mob.shouldJump();
        }

        @Override
        public void start() {
            this.mob.getJumpControl().jump();
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(movement.x, 0.5D, movement.z);
            this.mob.jumpCooldown = 10;
        }
    }

    private static final class SeekWaterGoal extends Goal {
        private final SilkBabyEntity mob;
        @Nullable
        private BlockPos target;
        private int searchCooldown;

        private SeekWaterGoal(SilkBabyEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.mob.isOnFire() || this.mob.isInWaterOrBubble()) {
                return false;
            }
            if (this.searchCooldown > 0) {
                this.searchCooldown--;
                return false;
            }
            this.searchCooldown = WATER_SEARCH_COOLDOWN_TICKS;
            this.target = findWater(this.mob);
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.mob.isOnFire()
                    && !this.mob.isInWaterOrBubble()
                    && this.target != null
                    && !this.mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, 1.2D);
        }

        @Override
        public void tick() {
            if (this.target != null && this.mob.tickCount % 10 == 0) {
                this.mob.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, 1.2D);
            }
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
            this.target = null;
            this.searchCooldown = WATER_SEARCH_COOLDOWN_TICKS;
        }

        @Nullable
        private static BlockPos findWater(SilkBabyEntity mob) {
            BlockPos center = mob.blockPosition();
            BlockPos nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 4, 16))) {
                if (!mob.level().getFluidState(pos).is(FluidTags.WATER)
                        || !mob.level().getFluidState(pos).isSource()
                        || !mob.level().getBlockState(pos.above()).getCollisionShape(mob.level(), pos.above()).isEmpty()) {
                    continue;
                }
                Path path = mob.getNavigation().createPath(pos, 0);
                if (path == null || !path.canReach()) {
                    continue;
                }
                double distance = pos.distSqr(center);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = pos.immutable();
                }
            }
            return nearest;
        }
    }
}
