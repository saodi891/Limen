package com.moonlightc.limen.entity.projectile;

import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.entity.monster.TowerGhostEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class TowerGhostBlueFlameEntity extends Entity implements GeoEntity {
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_LIFETIME = "Lifetime";
    private static final int LIFETIME_TICKS = 5 * 20;
    private static final float FIRE_DAMAGE = 2.0F;
    private static final RawAnimation BURN =
            RawAnimation.begin().thenLoop("animation.soul_fire.burn");

    @Nullable
    private UUID ownerUUID;
    private int lifetime = LIFETIME_TICKS;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public TowerGhostBlueFlameEntity(EntityType<? extends TowerGhostBlueFlameEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public TowerGhostBlueFlameEntity(EntityType<? extends TowerGhostBlueFlameEntity> entityType, Level level,
                                     TowerGhostEntity owner, double x, double y, double z) {
        this(entityType, level);
        this.ownerUUID = owner.getUUID();
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (--this.lifetime <= 0) {
            this.discard();
            return;
        }
        TowerGhostEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }
        AABB area = new AABB(
                this.getX() - 0.5D, this.getY(), this.getZ() - 0.5D,
                this.getX() + 0.5D, this.getY() + 1.5D, this.getZ() + 0.5D);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, area,
                living -> living.isAlive() && shouldDamage(owner, living))) {
            if (target instanceof net.minecraft.world.entity.player.Player player) {
                player.setSprinting(false);
            }
            target.setSecondsOnFire(2);
            target.hurt(this.damageSources().inFire(), FIRE_DAMAGE);
        }
    }

    private static boolean shouldDamage(TowerGhostEntity owner, LivingEntity target) {
        if (target == owner || target.getType().is(LimenEntityTypeTags.GHOSTS) || owner.isAlliedTo(target)) {
            return false;
        }
        if (owner.isHostileTo(target)) {
            return true;
        }
        if (target instanceof Mob mob && (mob.getTarget() == owner || mob.getLastHurtByMob() == owner)) {
            return true;
        }
        return target instanceof Monster && target.getLastHurtByMob() == owner;
    }

    @Nullable
    private TowerGhostEntity getOwner() {
        if (this.ownerUUID == null || !(this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        Entity owner = serverLevel.getEntity(this.ownerUUID);
        return owner instanceof TowerGhostEntity ghost ? ghost : null;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_OWNER)) {
            this.ownerUUID = tag.getUUID(TAG_OWNER);
        }
        this.lifetime = Math.max(0, tag.getInt(TAG_LIFETIME));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUUID != null) {
            tag.putUUID(TAG_OWNER, this.ownerUUID);
        }
        tag.putInt(TAG_LIFETIME, this.lifetime);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "burn", 0,
                state -> state.setAndContinue(BURN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
