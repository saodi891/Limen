package com.moonlightc.limen.entity.monster;

import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.registry.ModBlocks;
import com.moonlightc.limen.registry.ModEntities;
import com.moonlightc.limen.registry.ModMobEffects;
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
import net.minecraft.nbt.CompoundTag;
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

    // Clockwise lead arc (radians) for the wanted orbit point. A fixed lead keeps a steady gap
    // ahead of the detector so the body flies the circle instead of stalling on a near-zero step.
    private static final double ORBIT_LEAD_ANGLE = 0.8D;

    // Height above the locked player the orbit ring sits at. The detector rises/descends only to
    // hold this offset as the player moves vertically; it no longer bobs randomly while locked.
    private static final double ORBIT_HEIGHT = 1.6D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID lockedPlayerId;
    @Nullable
    private UUID commandedPamaId;
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
        this.goalSelector.addGoal(0, new RecallToPamaGoal(this));
        this.goalSelector.addGoal(4, new ReviveAutonomousSpiderGoal(this));
        this.goalSelector.addGoal(5, new ApproachDetectedPlayerGoal(this));
        this.goalSelector.addGoal(6, new HoverAroundGoal(this));
        // Look goals are suppressed while locking so updateLocking() can keep the head on the
        // locked player; otherwise the random look-around fights it for the look control.
        this.goalSelector.addGoal(7, new IdleLookAtPlayerGoal(this));
        this.goalSelector.addGoal(8, new IdleLookAroundGoal(this));

        this.targetSelector.addGoal(1, new RedstoneMachineHurtByTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(!this.hasEffect(ModMobEffects.SHOCKED.get()));
        if (!this.level().isClientSide && this.commandedPamaId == null) {
            this.updateLocking();
        }
    }

    public void commandRecallTo(PamaEntity pama) {
        if (!this.level().isClientSide && this.isAlive() && pama.isAlive()) {
            this.commandedPamaId = pama.getUUID();
        }
    }

    @Nullable
    private PamaEntity getCommandedPama() {
        if (this.commandedPamaId == null) {
            return null;
        }
        return PamaEntity.loadedIn(this.level()).stream()
                .filter(PamaEntity::isAlive)
                .filter(pama -> pama.getUUID().equals(this.commandedPamaId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.commandedPamaId != null) {
            tag.putUUID("CommandedPama", this.commandedPamaId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.commandedPamaId = tag.hasUUID("CommandedPama") ? tag.getUUID("CommandedPama") : null;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    // The model is only a head, so the body rotation is never rendered. Removing the head-yaw
    // clamp lets the head point straight at a locked player even while the body faces the orbit
    // tangent, instead of only glancing over when the body happens to swing around.
    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    public int getMaxHeadXRot() {
        return 90;
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
        if (player == null || !player.isAlive()) {
            this.lockedPlayerId = null;
            return null;
        }
        return player;
    }

    private void updateLocking() {
        Player lockedPlayer = this.getLockedPlayer();

        // A currently-locked player who drifts back out of activation range releases the lock.
        if (lockedPlayer != null && !this.isWithinLockRange(lockedPlayer)) {
            this.releaseLock();
            lockedPlayer = null;
        }

        if (lockedPlayer == null) {
            lockedPlayer = this.tryAcquireLock();
        }

        if (lockedPlayer == null) {
            return;
        }

        this.getLookControl().setLookAt(lockedPlayer, 30.0F, 30.0F);
        this.orbitLockedPlayer(lockedPlayer);
        this.applyGlowingWhenReady(lockedPlayer);
    }

    /**
     * Locking only activates once the detector has approached within the activation distance.
     * Detection (follow range) still lets it fly toward a far player, but the lock, orbit and
     * glowing all wait until it is close (~3 blocks).
     */
    @Nullable
    private Player tryAcquireLock() {
        double activate = this.getLockActivateDistance();
        Player candidate = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(activate),
                        player -> player.isAlive() && !player.isSpectator() && !player.isCreative())
                .stream()
                .filter(this::isWithinLockRange)
                .filter(player -> this.hasLineOfSight(player))
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);

        if (candidate == null) {
            return null;
        }

        this.lockedPlayerId = candidate.getUUID();
        // Lock immediately applies glowing, then the cooldown governs refreshes.
        this.glowingCooldownTicks = 0;
        return candidate;
    }

    private boolean isWithinLockRange(Player player) {
        double activate = this.getLockActivateDistance();
        return this.distanceToSqr(player) <= activate * activate;
    }

    private void releaseLock() {
        this.lockedPlayerId = null;
    }

    /**
     * A player the detector has spotted within its follow range. Used by the approach goal to fly
     * the detector into lock range.
     */
    @Nullable
    private Player findApproachTarget() {
        double range = this.getDetectionRange();
        return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(range),
                        player -> player.isAlive() && !player.isSpectator() && !player.isCreative())
                .stream()
                .filter(player -> this.hasLineOfSight(player))
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /**
     * Orbit by projecting a lead point further clockwise around the player than the detector's
     * current angle. A small per-tick step leaves the wanted position almost on top of the
     * detector, so the move control thinks it has arrived and never travels; a fixed lead arc
     * keeps a steady gap ahead so the body actually flies the circle.
     */
    private void orbitLockedPlayer(Player player) {
        double radius = LimenCommonConfig.PAMA_DETECTOR_LOCK_RADIUS.get();
        double dx = this.getX() - player.getX();
        double dz = this.getZ() - player.getZ();
        double currentAngle = Math.atan2(dz, dx);
        // Negative lead = clockwise when viewed from above. ~0.8 rad keeps the target roughly a
        // block and a half ahead along the arc so the detector keeps moving instead of stalling.
        double leadAngle = currentAngle - ORBIT_LEAD_ANGLE;
        double targetX = player.getX() + Math.cos(leadAngle) * radius;
        double targetZ = player.getZ() + Math.sin(leadAngle) * radius;
        // Fixed orbit height above the player: no idle bobbing. Vertical movement now happens only
        // to reach and hold this height as the player moves up or down.
        double targetY = player.getY() + ORBIT_HEIGHT;
        this.getMoveControl().setWantedPosition(targetX, targetY, targetZ, LimenCommonConfig.PAMA_DETECTOR_LOCK_SPEED.get());
    }

    private void applyGlowingWhenReady(Player player) {
        if (this.glowingCooldownTicks > 0) {
            this.glowingCooldownTicks--;
            return;
        }

        if (player.hasEffect(MobEffects.GLOWING)) {
            this.glowingCooldownTicks = LimenCommonConfig.PAMA_DETECTOR_LOCK_COOLDOWN_TICKS.get();
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, LimenCommonConfig.PAMA_DETECTOR_GLOWING_TICKS.get(), 0, false, true));
        this.glowingCooldownTicks = LimenCommonConfig.PAMA_DETECTOR_LOCK_COOLDOWN_TICKS.get();
    }

    private double getLockActivateDistance() {
        return LimenCommonConfig.PAMA_DETECTOR_LOCK_ACTIVATE_DISTANCE.get();
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

    private static class RecallToPamaGoal extends Goal {
        private static final double ARRIVAL_INFLATION = 1.0D;
        private final PamaDetectorEntity detector;
        @Nullable
        private PamaEntity pama;

        RecallToPamaGoal(PamaDetectorEntity detector) {
            this.detector = detector;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.pama = this.detector.getCommandedPama();
            if (this.detector.commandedPamaId != null && this.pama == null) {
                this.detector.commandedPamaId = null;
            }
            return this.pama != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.pama != null
                    && this.pama.isAlive()
                    && this.detector.commandedPamaId != null
                    && this.detector.commandedPamaId.equals(this.pama.getUUID());
        }

        @Override
        public void tick() {
            if (this.pama == null) {
                return;
            }

            if (this.pama.getBoundingBox().inflate(ARRIVAL_INFLATION)
                    .intersects(this.detector.getBoundingBox())) {
                this.pama.heal(5.0F);
                this.detector.commandedPamaId = null;
                this.detector.discard();
                return;
            }

            this.detector.getLookControl().setLookAt(this.pama, 30.0F, 30.0F);
            this.detector.getMoveControl().setWantedPosition(
                    this.pama.getX(),
                    this.pama.getY() + PamaEntity.COLLISION_HEIGHT * 0.5D,
                    this.pama.getZ(),
                    1.0D);
        }

        @Override
        public void stop() {
            this.pama = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
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
        private double wantedX;
        private double wantedY;
        private double wantedZ;
        private int repathCooldown;

        HoverAroundGoal(PamaDetectorEntity detector) {
            this.detector = detector;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.detector.lockedPlayerId == null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.detector.lockedPlayerId == null;
        }

        @Override
        public void start() {
            this.pickNewTarget();
        }

        @Override
        public void tick() {
            // FlyingMoveControl only steers for the single tick after setWantedPosition, so the
            // target must be re-issued every tick to keep the detector actually drifting (and
            // floating up/down); a one-shot call in start() would move it for one tick then stall.
            this.detector.getMoveControl().setWantedPosition(
                    this.wantedX, this.wantedY, this.wantedZ,
                    LimenCommonConfig.PAMA_DETECTOR_LOCK_SPEED.get());

            double distanceSq = this.detector.position().distanceToSqr(this.wantedX, this.wantedY, this.wantedZ);
            if (this.repathCooldown > 0) {
                this.repathCooldown--;
            }
            if (distanceSq < 2.0D || this.repathCooldown <= 0) {
                this.pickNewTarget();
            }
        }

        private void pickNewTarget() {
            // Full sphere of drift points so the detector wanders horizontally and vertically,
            // matching vanilla flying idle. The head-only model has no body to snap around.
            double heading = this.detector.getRandom().nextDouble() * (Math.PI * 2.0D);
            double distance = Mth.nextDouble(this.detector.getRandom(), 3.0D, 8.0D);
            this.wantedX = this.detector.getX() + Math.cos(heading) * distance;
            this.wantedZ = this.detector.getZ() + Math.sin(heading) * distance;
            this.wantedY = this.detector.getY() + Mth.nextDouble(this.detector.getRandom(), -3.0D, 4.0D);
            // Re-pick at least every few seconds even if the point is never quite reached.
            this.repathCooldown = 40 + this.detector.getRandom().nextInt(40);
        }
    }

    /**
     * Flies the detector toward a spotted player until it is inside lock-activation range, where
     * the locking/orbit logic in {@link #updateLocking()} takes over. Without this the detector
     * only had idle drift and never actually closed the distance to lock.
     */
    private static class ApproachDetectedPlayerGoal extends Goal {
        private final PamaDetectorEntity detector;
        @Nullable
        private Player target;

        ApproachDetectedPlayerGoal(PamaDetectorEntity detector) {
            this.detector = detector;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.detector.lockedPlayerId != null) {
                return false;
            }
            this.target = this.detector.findApproachTarget();
            return this.target != null && !this.detector.isWithinLockRange(this.target);
        }

        @Override
        public boolean canContinueToUse() {
            return this.detector.lockedPlayerId == null
                    && this.target != null
                    && this.target.isAlive()
                    && !this.detector.isWithinLockRange(this.target)
                    && this.detector.hasLineOfSight(this.target);
        }

        @Override
        public void tick() {
            if (this.target == null) {
                return;
            }
            // Aim just outside the player so the detector settles at orbit distance rather than
            // colliding, then updateLocking() picks it up once inside activation range.
            double targetY = this.target.getY() + 1.6D;
            this.detector.getMoveControl().setWantedPosition(
                    this.target.getX(), targetY, this.target.getZ(),
                    LimenCommonConfig.PAMA_DETECTOR_LOCK_SPEED.get());
        }

        @Override
        public void stop() {
            this.target = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /**
     * Vanilla-style "look at the nearest player" idle behaviour, but suppressed while locking so it
     * does not fight {@link #updateLocking()} for the head orientation.
     */
    private static class IdleLookAtPlayerGoal extends LookAtPlayerGoal {
        private final PamaDetectorEntity detector;

        IdleLookAtPlayerGoal(PamaDetectorEntity detector) {
            super(detector, Player.class, 12.0F);
            this.detector = detector;
        }

        @Override
        public boolean canUse() {
            return this.detector.lockedPlayerId == null && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.detector.lockedPlayerId == null && super.canContinueToUse();
        }
    }

    /**
     * Vanilla-style idle head sweeping, suppressed while locking so the head stays on the target.
     */
    private static class IdleLookAroundGoal extends RandomLookAroundGoal {
        private final PamaDetectorEntity detector;

        IdleLookAroundGoal(PamaDetectorEntity detector) {
            super(detector);
            this.detector = detector;
        }

        @Override
        public boolean canUse() {
            return this.detector.lockedPlayerId == null && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.detector.lockedPlayerId == null && super.canContinueToUse();
        }
    }
}
