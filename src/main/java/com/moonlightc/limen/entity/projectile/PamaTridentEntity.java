package com.moonlightc.limen.entity.projectile;

import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.network.ModNetworking;
import com.moonlightc.limen.network.PamaTridentImpactPacket;
import com.moonlightc.limen.registry.ModEntities;
import com.moonlightc.limen.registry.ModItems;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Custom charged trident projectile. Ports vanilla {@link net.minecraft.world.entity.projectile.ThrownTrident}
 * behaviour (loyalty return, channeling, foil, pickup, NBT) rather than subclassing it, because the vanilla
 * shooter constructor hard-codes {@code EntityType.TRIDENT} and keeps its stack/loyalty state private.
 *
 * <p>On its first entity or block impact it settles a one-shot Manhattan-radius area effect: one point of
 * void damage plus three seconds of Shocked to every non-redstone-machine living entity, and a client-side
 * expanding wave broadcast from the impact point.
 */
public class PamaTridentEntity extends AbstractArrow implements GeoEntity {
    public static final double IMPACT_RADIUS = 1.5D;
    private static final float BASE_DAMAGE = 8.0F;
    private static final float IMPACT_DAMAGE = 1.0F;
    private static final int SHOCKED_TICKS = 3 * 20;
    private static final int IMPACT_WAVE_RANGE = 64;

    private static final EntityDataAccessor<Byte> ID_LOYALTY =
            SynchedEntityData.defineId(PamaTridentEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ID_FOIL =
            SynchedEntityData.defineId(PamaTridentEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private ItemStack tridentItem = new ItemStack(Items.TRIDENT);
    private boolean dealtDamage;
    public int clientSideReturnTridentTickCount;

    public PamaTridentEntity(EntityType<? extends PamaTridentEntity> entityType, Level level) {
        super(entityType, level);
    }

    public PamaTridentEntity(Level level, LivingEntity shooter, ItemStack stack) {
        super(ModEntities.PAMA_TRIDENT.get(), shooter, level);
        this.tridentItem = stack.copy();
        this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(stack));
        this.entityData.set(ID_FOIL, stack.hasFoil());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ID_LOYALTY, (byte) 0);
        this.entityData.define(ID_FOIL, false);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity owner = this.getOwner();
        int loyalty = this.entityData.get(ID_LOYALTY);
        if (loyalty > 0 && (this.dealtDamage || this.isNoPhysics()) && owner != null) {
            if (!this.isAcceptibleReturnOwner()) {
                if (!this.level().isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }
                this.discard();
            } else {
                this.setNoPhysics(true);
                Vec3 toOwner = owner.getEyePosition().subtract(this.position());
                this.setPosRaw(this.getX(), this.getY() + toOwner.y * 0.015D * loyalty, this.getZ());
                if (this.level().isClientSide) {
                    this.yOld = this.getY();
                }
                double pull = 0.05D * loyalty;
                this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(toOwner.normalize().scale(pull)));
                if (this.clientSideReturnTridentTickCount == 0) {
                    this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
                }
                this.clientSideReturnTridentTickCount++;
            }
        }

        super.tick();
    }

    private boolean isAcceptibleReturnOwner() {
        Entity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }
        return !(owner instanceof net.minecraft.server.level.ServerPlayer) || !owner.isSpectator();
    }

    @Override
    protected ItemStack getPickupItem() {
        return this.tridentItem.copy();
    }

    public boolean isFoil() {
        return this.entityData.get(ID_FOIL);
    }

    @Override
    protected EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
        return this.dealtDamage ? null : super.findHitEntity(from, to);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        Entity target = hitResult.getEntity();
        float damage = BASE_DAMAGE;
        if (target instanceof LivingEntity livingTarget) {
            damage += EnchantmentHelper.getDamageBonus(this.tridentItem, livingTarget.getMobType());
        }

        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().trident(this, owner == null ? this : owner);
        this.dealtDamage = true;
        SoundEvent hitSound = SoundEvents.TRIDENT_HIT;
        if (target.hurt(source, damage)) {
            if (target.getType() == EntityType.ENDERMAN) {
                this.triggerImpact(hitResult.getLocation());
                return;
            }

            if (target instanceof LivingEntity livingTarget) {
                if (owner instanceof LivingEntity livingOwner) {
                    EnchantmentHelper.doPostHurtEffects(livingTarget, owner);
                    EnchantmentHelper.doPostDamageEffects(livingOwner, livingTarget);
                }
                this.doPostHurtEffects(livingTarget);
            }
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        float volume = 1.0F;
        if (this.level() instanceof ServerLevel serverLevel && serverLevel.isThundering() && this.isChanneling()) {
            net.minecraft.core.BlockPos strikePos = target.blockPosition();
            if (serverLevel.canSeeSky(strikePos)) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt != null) {
                    bolt.moveTo(Vec3.atBottomCenterOf(strikePos));
                    bolt.setCause(owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null);
                    serverLevel.addFreshEntity(bolt);
                    hitSound = SoundEvents.TRIDENT_THUNDER;
                    volume = 5.0F;
                }
            }
        }
        this.playSound(hitSound, volume, 1.0F);

        this.triggerImpact(hitResult.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        this.triggerImpact(hitResult.getLocation());
    }

    /**
     * Settles the Manhattan-radius area effect at the impact point. Fires on every discrete hit event, so a
     * trident that strikes a target and then lands triggers it on both the strike and the landing. Server-side
     * only; onHit* are event-driven (not re-fired on reload) so a trident stuck in the ground never repeats it.
     */
    private void triggerImpact(Vec3 center) {
        if (this.level().isClientSide) {
            return;
        }

        AABB bounds = new AABB(center, center).inflate(IMPACT_RADIUS);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, bounds,
                candidate -> candidate.isAlive()
                        && !candidate.isSpectator()
                        && !candidate.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)
                        && manhattanDistanceToBox(center, candidate.getBoundingBox()) <= IMPACT_RADIUS);

        DamageSource voidDamage = this.damageSources().fellOutOfWorld();
        for (LivingEntity target : targets) {
            target.hurt(voidDamage, IMPACT_DAMAGE);
            target.addEffect(new MobEffectInstance(ModMobEffects.SHOCKED.get(), SHOCKED_TICKS, 0, false, true));
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            ModNetworking.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            center.x, center.y, center.z, IMPACT_WAVE_RANGE, serverLevel.dimension())),
                    new PamaTridentImpactPacket(center.x, center.y, center.z));
        }
    }

    private static double manhattanDistanceToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0D), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0D), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0D), point.z - box.maxZ);
        return dx + dy + dz;
    }

    public boolean isChanneling() {
        return EnchantmentHelper.hasChanneling(this.tridentItem);
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player)
                || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void playerTouch(Player player) {
        if (this.ownedBy(player) || this.getOwner() == null) {
            super.playerTouch(player);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Trident", 10)) {
            this.tridentItem = ItemStack.of(tag.getCompound("Trident"));
        }
        this.dealtDamage = tag.getBoolean("DealtDamage");
        this.entityData.set(ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(this.tridentItem));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Trident", this.tridentItem.save(new CompoundTag()));
        tag.putBoolean("DealtDamage", this.dealtDamage);
    }

    @Override
    public void tickDespawn() {
        int loyalty = this.entityData.get(ID_LOYALTY);
        if (this.pickup != AbstractArrow.Pickup.ALLOWED || loyalty <= 0) {
            super.tickDespawn();
        }
    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Static model: no animation controllers needed.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
