package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class RedstoneMachineEvents {
    /** A hurt redstone machine rallies every ally within this radius onto the attacker. */
    private static final double ALLY_ALERT_RADIUS = 20.0D;

    private RedstoneMachineEvents() {
    }

    /**
     * Collective aggro: when a redstone machine is attacked by an outsider, every redstone machine within
     * {@link #ALLY_ALERT_RADIUS} blocks targets that attacker. Same-faction hits are ignored so machines
     * never rally against each other.
     */
    @SubscribeEvent
    public static void onRedstoneMachineHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !victim.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !attacker.isAlive()) {
            return;
        }
        if (attacker.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
            return;
        }
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }

        double radiusSq = ALLY_ALERT_RADIUS * ALLY_ALERT_RADIUS;
        for (Mob ally : victim.level().getEntitiesOfClass(Mob.class,
                victim.getBoundingBox().inflate(ALLY_ALERT_RADIUS),
                m -> m.isAlive()
                        && m.getTarget() == null
                        && m.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)
                        && m.distanceToSqr(victim) <= radiusSq)) {
            ally.setTarget(attacker);
        }
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
            event.setCanceled(true);
        }
    }

    /**
     * Faction targeting: a redstone machine never aggros another redstone machine, no matter which goal tried
     * to set it (retaliation from friendly-fire arrows, nearest-target scans, etc.). Cancelling the target
     * change leaves the machine's existing target untouched.
     */
    @SubscribeEvent
    public static void onRedstoneMachineChangeTarget(LivingChangeTargetEvent event) {
        if (!event.getEntity().getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
            return;
        }
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget != null && newTarget.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || mob.tickCount % 5 != 0) {
            return;
        }

        if (!mob.getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
            return;
        }

        double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (followRange <= 0.0D) {
            followRange = 16.0D;
        }

        Player glowingTarget = mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(followRange),
                        player -> player.isAlive()
                                && !player.isCreative()
                                && !player.isSpectator()
                                && player.hasEffect(MobEffects.GLOWING))
                .stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);

        if (glowingTarget != null) {
            mob.setTarget(glowingTarget);
        }
    }
}
