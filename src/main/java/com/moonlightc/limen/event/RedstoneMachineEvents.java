package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class RedstoneMachineEvents {
    private RedstoneMachineEvents() {
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().getType().is(LimenEntityTypeTags.REDSTONE_MACHINES)) {
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
