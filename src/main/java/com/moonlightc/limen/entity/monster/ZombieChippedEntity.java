package com.moonlightc.limen.entity.monster;

import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ZombieChippedEntity extends Zombie implements GeoEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public ZombieChippedEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.FOLLOW_RANGE, LimenCommonConfig.ZOMBIE_CHIPPED_FOLLOW_RANGE.get());
    }

    @Override
    public boolean isSunSensitive() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomSource, DifficultyInstance difficulty) {
    }

    @Override
    protected void populateDefaultEquipmentEnchantments(RandomSource randomSource, DifficultyInstance difficulty) {
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData, @Nullable net.minecraft.nbt.CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, dataTag);
        this.setBaby(false);
        this.setCanBreakDoors(false);
        this.setCanPickUpLoot(false);
        if (this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE) != null) {
            this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0D);
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, ItemStack.EMPTY);
            this.setDropChance(slot, 0.0F);
        }

        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isBaby()) {
            this.setBaby(false);
        }
        if (!this.level().isClientSide && this.isAlive() && this.isEyeInFluid(FluidTags.WATER)) {
            this.convertToVanillaZombie();
        }
    }

    private void convertToVanillaZombie() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.maybeDropDamagedChip();
            Zombie zombie = this.convertTo(EntityType.ZOMBIE, true);
            if (zombie != null) {
                zombie.setCanBreakDoors(false);
                zombie.setCanPickUpLoot(false);
                if (zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE) != null) {
                    zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0D);
                }
                zombie.setPersistenceRequired();
            }
        }
    }

    private void maybeDropDamagedChip() {
        if (this.random.nextFloat() < LimenCommonConfig.ZOMBIE_CHIPPED_CHIP_DROP_CHANCE.get()) {
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
