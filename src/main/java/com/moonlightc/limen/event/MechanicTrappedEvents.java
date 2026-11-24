package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import com.moonlightc.limen.entity.MechanicTrappedState;
import com.moonlightc.limen.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class MechanicTrappedEvents {
    private MechanicTrappedEvents() {
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Villager villager
                && MechanicTrappedState.isMechanic(villager)) {
            MechanicTrappedState.initialize(villager, !event.loadedFromDisk());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Villager villager
                && MechanicTrappedState.isInitialized(villager)) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    MechanicTrappedState.packet(villager));
        }
    }

    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof Villager villager && MechanicTrappedState.isTrapped(villager)) {
            interact(event, villager);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Villager villager && MechanicTrappedState.isTrapped(villager)) {
            interact(event, villager);
        }
    }

    private static void interact(PlayerInteractEvent event, Villager villager) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(villager.level().isClientSide));
        Player player = event.getEntity();
        if (!villager.level().isClientSide && villager.isAlive() && event.getHand() == InteractionHand.MAIN_HAND
                && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            MechanicTrappedState.rescue(villager);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (isIllagerAttacker(event.getEntity()) && event.getNewTarget() instanceof Villager villager
                && MechanicTrappedState.isTrapped(villager)) {
            // Set null rather than cancelling, which could keep an already selected trapped target.
            event.setNewTarget(null);
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof Villager villager && MechanicTrappedState.isTrapped(villager)
                && isIllagerAttacker(event.getSource().getEntity())) {
            // DamageSource#getEntity also identifies the shooter/caster of arrows and evoker fangs.
            event.setCanceled(true);
        }
    }

    private static boolean isIllagerAttacker(Entity entity) {
        return entity instanceof AbstractIllager || entity instanceof Ravager
                || entity != null && entity.getType().is(LimenEntityTypeTags.ILLAGER_FACTION)
                || entity instanceof Vex vex && isIllagerAttacker(vex.getOwner());
    }
}
