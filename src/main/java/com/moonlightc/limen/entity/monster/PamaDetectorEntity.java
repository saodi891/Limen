package com.moonlightc.limen.entity.monster;

import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.registry.ModBlocks;
import com.moonlightc.limen.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class PamaDetectorEntity extends FlyingMob implements Enemy, GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.pama_detector.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.pama_detector.fly");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID lockedPlayerId;
    private int glowingCooldownTicks = LimenCommonConfig.PAMA_DETECTOR_LOCK_COOLDOWN_TICKS.get();

    public PamaDetectorEntity(EntityType<? extends FlyingMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FlyingMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, LimenCommonConfig.PAMA_DETECTOR_HEALTH.get())
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, LimenCommonConfig.PAMA_DETECTOR_FOLLOW_RANGE.get())
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(4, new ReviveAutonomousSpiderGoal(this));
        this.goalSelector.addGoal(5, new HoverAroundGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new RedstoneMachineHurtByTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        if (!this.level().isClientSide) {
            this.updateLocking();
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(FLY);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Nullable
    private Player getLockedPlayer() {
        if (this.lockedPlayerId == null) {
            return null;
        }
        Player player = this.level().getPlayerByUUID(this.lockedPlayerId);
        if (player == null || !player.isAlive() || this.distanceToSqr(player) > this.getDetectionRange() * this.getDetectionRange() * 1.6D) {
            this.lockedPlayerId = null;
            return null;
        }
        return player;
    }

    private void updateLocking() {
        Player lockedPlayer = this.getLockedPlayer();
        if (lockedPlayer != null && this.getLockSlotIndex(lockedPlayer) >= 2) {
            this.lockedPlayerId = null;
            lockedPlayer = null;
        }

        LivingEntity angerTarget = this.getTarget();
        if (lockedPlayer == null
                && angerTarget instanceof Player targetPlayer
                && targetPlayer.isAlive()
                && this.countLockingDetectors(targetPlayer) < 2) {
            this.lockedPlayerId = targetPlayer.getUUID();
            this.glowingCooldownTicks = LimenCommonConfig.PAMA_DETECTOR_LOCK_COOLDOWN_TICKS.get();
            lockedPlayer = targetPlayer;
        }

        if (lockedPlayer == null) {
            lockedPlayer = this.findLockablePlayer();
            if (lockedPlayer != null) {
                this.lockedPlayerId = lockedPlayer.getUUID();
                this.glowingCooldownTicks = LimenCommonConfig.PAMA_DETECTOR_LOCK_COOLDOWN_TICKS.get();
            }
        }

        if (lockedPlayer == null) {
            return;
        }

        if (angerTarget instanceof Player targetPlayer && targetPlayer.isAlive() && this.countLockingDetectors(targetPlayer) < 2) {
            this.lockedPlayerId = targetPlayer.getUUID();
            lockedPlayer = targetPlayer;
        }

        this.getLookControl().setLookAt(lockedPlayer, 30.0F, 30.0F);
        this.orbitLockedPlayer(lockedPlayer);
        this.applyGlowingWhenReady(lockedPlayer);
    }

    @Nullable
    private Player findLockablePlayer() {
        double range = this.getDetectionRange();
        return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(range), Player::isAlive)
                .stream()
                .filter(player -> this.hasLineOfSight(player))
                .filter(player -> this.countLockingDetectors(player) < 2)
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void orbitLockedPlayer(Player player) {
        int slot = this.getLockSlot(player);
        double radius = LimenCommonConfig.PAMA_DETECTOR_LOCK_RADIUS.get();
        double baseAngle = (this.tickCount * 0.08D) + (slot * Math.PI);
        double targetX = player.getX() + Math.cos(baseAngle) * radius;
        double targetY = player.getY() + 1.6D + Math.sin(this.tickCount * 0.05D) * 0.35D;
        double targetZ = player.getZ() + Math.sin(baseAngle) * radius;
        this.getMoveControl().setWantedPosition(targetX, targetY, targetZ, LimenCommonConfig.PAMA_DETECTOR_LOCK_SPEED.get());
    }

    private void applyGlowingWhenReady(Player player) {
        if (this.glowingCooldownTicks > 0) {
            this.glowingCooldownTicks--;
            return;
        }

        if (player.hasEffect(MobEffects.GLOWING)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, LimenCommonConfig.PAMA_DETECTOR_GLOWING_TICKS.get(), 0, false, true));
        this.glowingCooldownTicks = LimenCommonConfig.PAMA_DETECTOR_LOCK_COOLDOWN_TICKS.get();
    }

    private int getLockSlot(Player player) {
        int index = this.getLockSlotIndex(player);
        return index <= 0 ? 0 : 1;
    }

    private int getLockSlotIndex(Player player) {
        List<PamaDetectorEntity> lockers = this.getLockingDetectors(player);
        lockers.sort(Comparator.comparing(entity -> entity.getUUID().toString()));
        return lockers.indexOf(this);
    }

    private int countLockingDetectors(Player player) {
        return this.getLockingDetectors(player).size();
    }

    private List<PamaDetectorEntity> getLockingDetectors(Player player) {
        double range = this.getDetectionRange() * 1.8D;
        return this.level().getEntitiesOfClass(PamaDetectorEntity.class, player.getBoundingBox().inflate(range),
                detector -> detector.isAlive() && player.getUUID().equals(detector.lockedPlayerId));
    }

    private double getDetectionRange() {
        return this.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Nullable
    private BlockPos findNearestSpiderRemains(int range) {
        BlockPos center = this.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int x = center.getX() - range; x <= center.getX() + range; x++) {
            for (int y = center.getY() - range; y <= center.getY() + range; y++) {
                for (int z = center.getZ() - range; z <= center.getZ() + range; z++) {
                    cursor.set(x, y, z);
                    if (!this.level().getBlockState(cursor).is(ModBlocks.AUTONOMOUS_SPIDER_REMAINS.get())) {
                        continue;
                    }

                    double distance = cursor.distToCenterSqr(this.position());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = cursor.immutable();
                    }
                }
            }
        }

        return nearest;
    }

    private void reviveAutonomousSpiderAt(BlockPos remainsPos) {
        if (this.level().isClientSide || !this.level().getBlockState(remainsPos).is(ModBlocks.AUTONOMOUS_SPIDER_REMAINS.get())) {
            return;
        }

        this.level().removeBlock(remainsPos, false);

        AutonomousSpiderEntity spider = ModEntities.AUTONOMOUS_SPIDER.get().create(this.level());
        if (spider == null) {
            return;
        }

        spider.moveTo(remainsPos.getX() + 0.5D, remainsPos.getY(), remainsPos.getZ() + 0.5D, this.getYRot(), this.getXRot());
        spider.setHealth(spider.getMaxHealth() * 0.5F);
        this.level().addFreshEntity(spider);
        this.discard();
    }

    private static boolean isRedstoneMachine(LivingEntity entity) {
        return entity.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES);
    }

    private static class ReviveAutonomousSpiderGoal extends Goal {
        private static final int SEARCH_RANGE = 10;
        private static final double ARRIVAL_DISTANCE_SQR = 1.0D;

        private final PamaDetectorEntity detector;
        @Nullable
        private BlockPos targetPos;

        ReviveAutonomousSpiderGoal(PamaDetectorEntity detector) {
            this.detector = detector;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.targetPos = this.detector.findNearestSpiderRemains(SEARCH_RANGE);
            return this.targetPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.targetPos != null
                    && this.detector.level().getBlockState(this.targetPos).is(ModBlocks.AUTONOMOUS_SPIDER_REMAINS.get());
        }

        @Override
        public void tick() {
            if (this.targetPos == null) {
                return;
            }

            Vec3 targetCenter = Vec3.atCenterOf(this.targetPos);
            this.detector.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z, 30.0F, 30.0F);
            this.detector.getMoveControl().setWantedPosition(targetCenter.x, targetCenter.y + 0.1D, targetCenter.z, 1.0D);

            if (this.detector.position().distanceToSqr(targetCenter) <= ARRIVAL_DISTANCE_SQR) {
                this.detector.reviveAutonomousSpiderAt(this.targetPos);
            }
        }

        @Override
        public void stop() {
            this.targetPos = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    private static class RedstoneMachineHurtByTargetGoal extends Goal {
        private final PamaDetectorEntity detector;
        @Nullable
        private LivingEntity attacker;

        RedstoneMachineHurtByTargetGoal(PamaDetectorEntity detector) {
            this.detector = detector;
        }

        @Override
        public boolean canUse() {
            LivingEntity lastAttacker = this.detector.getLastHurtByMob();
            if (lastAttacker == null || !lastAttacker.isAlive() || isRedstoneMachine(lastAttacker)) {
                return false;
            }
            this.attacker = lastAttacker;
            return true;
        }

        @Override
        public void start() {
            if (this.attacker != null) {
                this.detector.setTarget(this.attacker);
            }
        }
    }

    private static class HoverAroundGoal extends Goal {
        private final PamaDetectorEntity detector;

        HoverAroundGoal(PamaDetectorEntity detector) {
            this.detector = detector;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.detector.lockedPlayerId == null
                    && (!this.detector.getMoveControl().hasWanted()
                    || this.detector.getRandom().nextInt(30) == 0);
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            double x = this.detector.getX() + Mth.nextDouble(this.detector.getRandom(), -8.0D, 8.0D);
            double y = this.detector.getY() + Mth.nextDouble(this.detector.getRandom(), -3.0D, 4.0D);
            double z = this.detector.getZ() + Mth.nextDouble(this.detector.getRandom(), -8.0D, 8.0D);
            this.detector.getMoveControl().setWantedPosition(x, y, z, LimenCommonConfig.PAMA_DETECTOR_IDLE_SPEED.get());
        }
    }
}
