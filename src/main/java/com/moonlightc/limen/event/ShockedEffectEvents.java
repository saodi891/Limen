package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-authoritative immobilization for the Shocked effect. The movement-speed modifier on
 * {@link com.moonlightc.limen.effect.ShockedMobEffect} roots the attribute; these handlers additionally zero
 * velocity, stop AI, cancel jumps, and absorb knockback so every living entity is truly held for the
 * duration. No pre-shock state is stored, so movement/AI resume naturally the moment the effect ends.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class ShockedEffectEvents {
    private ShockedEffectEvents() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.hasEffect(ModMobEffects.SHOCKED.get())) {
            return;
        }

        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0D, Math.min(movement.y, 0.0D), 0.0D);
        entity.hurtMarked = true;

        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            mob.setYya(0.0F);
            mob.setJumping(false);
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ModMobEffects.SHOCKED.get())) {
            Vec3 movement = entity.getDeltaMovement();
            entity.setDeltaMovement(movement.x, Math.min(movement.y, 0.0D), movement.z);
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().hasEffect(ModMobEffects.SHOCKED.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || !player.hasEffect(ModMobEffects.SHOCKED.get())) {
            return;
        }

        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, Math.min(movement.y, 0.0D), 0.0D);
        player.setSprinting(false);
    }
}
