package com.moonlightc.limen.entity.monster;

import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SkeletonChippedEntity extends Skeleton implements GeoEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public SkeletonChippedEntity(EntityType<? extends Skeleton> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(false);
        this.setLeftHanded(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Skeleton.createAttributes();
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomSource, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    protected void populateDefaultEquipmentEnchantments(RandomSource randomSource, DifficultyInstance difficulty) {
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (vehicle instanceof Spider) {
            return false;
        }

        return super.startRiding(vehicle, force);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData, @Nullable net.minecraft.nbt.CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, dataTag);
        this.setCanPickUpLoot(false);
        this.setLeftHanded(false);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.MAINHAND) {
                this.setItemSlot(slot, ItemStack.EMPTY);
                this.setDropChance(slot, 0.0F);
            }
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.isLeftHanded()) {
            this.setLeftHanded(false);
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.MAINHAND && !this.getItemBySlot(slot).isEmpty()) {
                this.setItemSlot(slot, ItemStack.EMPTY);
                this.setDropChance(slot, 0.0F);
            }
        }

        if (!this.getMainHandItem().is(Items.BOW)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        if (!this.level().isClientSide && this.isAlive() && this.isEyeInFluid(FluidTags.WATER)) {
            this.convertToVanillaSkeleton();
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource damageSource, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(damageSource, looting, recentlyHit);
        this.maybeDropDamagedChip();
    }

    private void convertToVanillaSkeleton() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.maybeDropDamagedChip();
            Skeleton skeleton = this.convertTo(EntityType.SKELETON, true);
            if (skeleton != null) {
                skeleton.setCanPickUpLoot(false);
                skeleton.setLeftHanded(false);
                skeleton.setPersistenceRequired();
            }
        }
    }

    private void maybeDropDamagedChip() {
        if (this.random.nextFloat() < LimenCommonConfig.SKELETON_CHIPPED_CHIP_DROP_CHANCE.get()) {
            this.spawnAtLocation(ModItems.DAMAGED_CHIP.get());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
