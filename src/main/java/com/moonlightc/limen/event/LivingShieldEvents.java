package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.LivingShieldItem;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class LivingShieldEvents {
    private static final double ROAR_RADIUS = 2.0D;

    private LivingShieldEvents() {
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide) {
            return;
        }

        ItemStack stack = holder.getUseItem();
        if (!stack.is(ModItems.LIVING_SHIELD.get()) || !LivingShieldItem.isRoarReady(stack, holder.level().getGameTime())) {
            return;
        }

        LivingShieldItem.markRoarUsed(stack, holder.level().getGameTime());
        holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 1.0F);

        for (LivingEntity target : holder.level().getEntitiesOfClass(LivingEntity.class, holder.getBoundingBox().inflate(ROAR_RADIUS), living -> shouldAffect(holder, living))) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 3));
        }
    }

    private static boolean shouldAffect(LivingEntity holder, LivingEntity target) {
        if (target == holder || !target.isAlive() || target.isAlliedTo(holder) || holder.isAlliedTo(target)) {
            return false;
        }

        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }

        if (target instanceof TamableAnimal tamable && tamable.isTame()) {
            return tamable.getOwnerUUID() != null && !tamable.getOwnerUUID().equals(holder.getUUID());
        }

        if (target instanceof Mob mob) {
            return mob.getTarget() == holder || mob.getLastHurtByMob() == holder;
        }

        return false;
    }
}
