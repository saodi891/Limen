package com.moonlightc.limen.entity.monster;

import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.registry.ModBlocks;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class AutonomousSpiderEntity extends Spider implements GeoEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int skill1CooldownTicks = LimenCommonConfig.AUTONOMOUS_SPIDER_SKILL1_COOLDOWN_TICKS.get();
    private int skill1WarmupTicks;
    private boolean remainsPlaced;

    public AutonomousSpiderEntity(EntityType<? extends Spider> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes()
                .add(Attributes.MAX_HEALTH, LimenCommonConfig.AUTONOMOUS_SPIDER_HEALTH.get())
                .add(Attributes.ARMOR, LimenCommonConfig.AUTONOMOUS_SPIDER_ARMOR.get());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            this.tickSkill1();
        }
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource damageSource) {
        super.die(damageSource);

        if (!this.level().isClientSide && !this.remainsPlaced) {
            this.placeRemains();
        }
    }

    private void tickSkill1() {
        if (this.skill1CooldownTicks > 0) {
            this.skill1CooldownTicks--;
        }

        if (this.skill1WarmupTicks > 0) {
            this.skill1WarmupTicks--;
            if (this.skill1WarmupTicks == 0) {
                this.releaseShockPulse();
                this.skill1CooldownTicks = LimenCommonConfig.AUTONOMOUS_SPIDER_SKILL1_COOLDOWN_TICKS.get();
            }
            return;
        }

        LivingEntity target = this.getTarget();
        if (this.skill1CooldownTicks > 0 || !this.canUseSkill1(target)) {
            return;
        }

        this.skill1WarmupTicks = LimenCommonConfig.AUTONOMOUS_SPIDER_SKILL1_WARMUP_TICKS.get();
        this.getNavigation().stop();
    }

    private boolean canUseSkill1(LivingEntity target) {
        if (target == null || !target.isAlive() || target.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
            return false;
        }

        double radius = LimenCommonConfig.AUTONOMOUS_SPIDER_SKILL1_RADIUS.get();
        if (this.distanceToSqr(target) > radius * radius) {
            return false;
        }

        return this.hasLineOfSight(target);
    }

    private void releaseShockPulse() {
        double radius = LimenCommonConfig.AUTONOMOUS_SPIDER_SKILL1_RADIUS.get();
        int shockedTicks = LimenCommonConfig.AUTONOMOUS_SPIDER_SKILL1_SHOCKED_TICKS.get();
        AABB area = this.getBoundingBox().inflate(radius);
        List<Player> players = this.level().getEntitiesOfClass(Player.class, area,
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator());

        for (Player player : players) {
            if (this.distanceToSqr(player) <= radius * radius) {
                player.addEffect(new MobEffectInstance(ModMobEffects.SHOCKED.get(), shockedTicks, 0, false, true));
                Vec3 push = player.position().subtract(this.position());
                if (push.lengthSqr() > 0.0001D) {
                    Vec3 normalized = push.normalize().scale(0.35D);
                    player.push(normalized.x, 0.08D, normalized.z);
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.hurtMarked = true;
                    }
                }
            }
        }
        this.playSound(SoundEvents.ANVIL_LAND, 1.0F, 1.15F);
    }

    private void placeRemains() {
        this.remainsPlaced = true;
        BlockState remains = ModBlocks.AUTONOMOUS_SPIDER_REMAINS.get().defaultBlockState();
        BlockPos[] candidates = new BlockPos[] {
                this.blockPosition(),
                this.blockPosition().above()
        };

        for (BlockPos pos : candidates) {
            BlockState stateAtPos = this.level().getBlockState(pos);
            if (stateAtPos.isAir() || stateAtPos.is(ModBlocks.STRUCTURE_VOID_BLOCK.get())) {
                this.level().setBlock(pos, remains, 3);
                return;
            }
        }

        this.spawnAtLocation(new ItemStack(ModBlocks.AUTONOMOUS_SPIDER_REMAINS.get()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
