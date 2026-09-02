package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.item.ChainswordItem;
import com.moonlightc.limen.network.ChainswordGrindPacket;
import com.moonlightc.limen.network.ChainswordHeatPacket;
import com.moonlightc.limen.network.ModNetworking;
import com.moonlightc.limen.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chainsword sustained attack and overheat cycle.
 *
 * <p>Holding left-click past a short wind-up switches into a grind that deals area damage on a
 * fixed interval whether or not anything is in range. Overheat is driven by how long the grind has
 * been running, not by how many settlements landed: once sustained attack has run for its allowed
 * duration the weapon overheats, losing durability and refusing to attack for a cooldown.
 *
 * <p>The wind-up before the first grind tick can still land a normal swing, but that swing does not
 * count toward overheat — the clock starts at the first grind settlement.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class ChainswordEvents {
    /** Players currently holding the attack key with a chainsword, reported by the client. */
    private static final Map<UUID, Boolean> HOLDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> HOLD_START = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DAMAGE = new ConcurrentHashMap<>();
    /** Gametime the current grind (first settlement) began, per player. Drives overheat timing. */
    private static final Map<UUID, Long> GRIND_START = new ConcurrentHashMap<>();
    /** Gametime the overheat cooldown ends, per player. */
    private static final Map<UUID, Long> OVERHEAT_UNTIL = new ConcurrentHashMap<>();

    private ChainswordEvents() {
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        UUID id = player.getUUID();
        if (holding) {
            HOLDING.put(id, Boolean.TRUE);
            HOLD_START.putIfAbsent(id, player.level().getGameTime());
        } else {
            HOLDING.remove(id);
            HOLD_START.remove(id);
            LAST_DAMAGE.remove(id);
            // Releasing the trigger lets the weapon cool off; heat only builds within one hold.
            if (GRIND_START.remove(id) != null) {
                broadcastGrinding(player, false);
            }
        }
    }

    /**
     * Announce grind start/stop to every client tracking this player, so the sawing arm pose renders
     * in third person for other players too, not just for the holder.
     */
    private static void broadcastGrinding(Player player, boolean grinding) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ModNetworking.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> serverPlayer),
                new ChainswordGrindPacket(serverPlayer.getId(), grinding));
    }

    /** True while the weapon is overheating and must not attack. */
    public static boolean isOverheated(Player player, long gameTime) {
        Long until = OVERHEAT_UNTIL.get(player.getUUID());
        return until != null && gameTime < until;
    }

    /** Blocks normal swings during the overheat cooldown. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof ChainswordItem)) {
            return;
        }
        if (isOverheated(player, player.level().getGameTime())) {
            event.setCanceled(true);
        }
    }

    /**
     * The chainsword grinds rather than launches: targets it hits keep only a fraction of the
     * knockback they would normally take. Covers both normal swings and grind ticks, since both go
     * through a player attack with the chainsword in hand.
     */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.getLastHurtByMob() instanceof Player attacker)) {
            return;
        }
        if (!(attacker.getMainHandItem().getItem() instanceof ChainswordItem)) {
            return;
        }
        event.setStrength((float) (event.getStrength() * LimenCommonConfig.CHAINSWORD_KNOCKBACK_SCALE.get()));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }

        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        ItemStack weapon = player.getMainHandItem();

        if (!(weapon.getItem() instanceof ChainswordItem)) {
            clearState(id);
            return;
        }

        if (isOverheated(player, now)) {
            // Cannot grind while overheating; heat stays cleared until the trigger is pulled again.
            LAST_DAMAGE.remove(id);
            HOLD_START.remove(id);
            return;
        }
        OVERHEAT_UNTIL.remove(id);

        if (!Boolean.TRUE.equals(HOLDING.get(id))) {
            HOLD_START.remove(id);
            LAST_DAMAGE.remove(id);
            GRIND_START.remove(id);
            return;
        }

        Long startTick = HOLD_START.get(id);
        if (startTick == null || now - startTick < LimenCommonConfig.CHAINSWORD_HOLD_THRESHOLD_TICKS.get()) {
            return;
        }

        int interval = LimenCommonConfig.CHAINSWORD_SUSTAINED_INTERVAL_TICKS.get();
        Long lastDamage = LAST_DAMAGE.get(id);
        if (lastDamage != null && now - lastDamage < interval) {
            return;
        }

        LAST_DAMAGE.put(id, now);
        // The grind clock starts at the first settlement, so the wind-up swing is not counted.
        boolean firstSettlement = GRIND_START.putIfAbsent(id, now) == null;
        if (firstSettlement) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.CHAINSWORD_START.get(), player.getSoundSource(), 0.9F, 1.0F);
            broadcastGrinding(player, true);
        }
        grind(player, weapon);

        if (player instanceof ServerPlayer serverPlayer) {
            ModNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new ChainswordHeatPacket(getHeatProgress(player)));
        }

        Long grindStart = GRIND_START.get(id);
        if (grindStart != null && now - grindStart >= LimenCommonConfig.CHAINSWORD_OVERHEAT_DURATION_TICKS.get() - interval) {
            triggerOverheat(player, weapon, now);
        }
    }

    private static void clearState(UUID id) {
        HOLD_START.remove(id);
        LAST_DAMAGE.remove(id);
        GRIND_START.remove(id);
    }

    /**
     * Area damage around the player. Each settlement deals its damage several times in a row (the
     * chainsword's teeth biting), and costs one durability regardless of whether anything was hit.
     */
    private static void grind(Player player, ItemStack weapon) {
        float damage = (float) (double) LimenCommonConfig.CHAINSWORD_SUSTAINED_DAMAGE.get();
        double radius = LimenCommonConfig.CHAINSWORD_SUSTAINED_RADIUS.get();
        int hits = LimenCommonConfig.CHAINSWORD_HITS_PER_SETTLEMENT.get();

        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, box, living ->
                living != player
                        && living.isAlive()
                        && !living.isSpectator()
                        && player.distanceTo(living) <= radius);

        for (LivingEntity target : targets) {
            float total = damage + EnchantmentHelper.getDamageBonus(weapon, target.getMobType());
            for (int i = 0; i < hits; i++) {
                if (!target.isAlive()) {
                    break;
                }
                // Bypass the vanilla invulnerability window so every bite lands.
                int previousInvulnerable = target.invulnerableTime;
                target.invulnerableTime = 0;
                DamageSource source = player.damageSources().playerAttack(player);
                if (target.hurt(source, total)) {
                    EnchantmentHelper.doPostHurtEffects(target, player);
                    EnchantmentHelper.doPostDamageEffects(player, target);
                } else {
                    target.invulnerableTime = previousInvulnerable;
                    break;
                }
            }
        }

        // One durability per grind settlement regardless of whether anything was hit.
        weapon.hurtAndBreak(1, player, living -> living.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        player.causeFoodExhaustion(0.05F);
    }

    private static void triggerOverheat(Player player, ItemStack weapon, long now) {
        UUID id = player.getUUID();
        int overheatTicks = LimenCommonConfig.CHAINSWORD_OVERHEAT_TICKS.get();

        OVERHEAT_UNTIL.put(id, now + overheatTicks);
        GRIND_START.remove(id);
        LAST_DAMAGE.remove(id);
        HOLD_START.remove(id);
        broadcastGrinding(player, false);

        weapon.hurtAndBreak(LimenCommonConfig.CHAINSWORD_OVERHEAT_DURABILITY_COST.get(), player,
                living -> living.broadcastBreakEvent(EquipmentSlot.MAINHAND));

        // Vanilla item cooldown drives the on-icon cooldown sweep clientside.
        player.getCooldowns().addCooldown(weapon.getItem(), overheatTicks);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.CHAINSWORD_OVERHEAT.get(), player.getSoundSource(), 1.0F, 1.0F);
    }

    /**
     * Heat progress 0..1 based on how long the grind has been running, used clientside for the
     * accumulating cooldown overlay.
     */
    public static float getHeatProgress(Player player) {
        Long grindStart = GRIND_START.get(player.getUUID());
        int max = LimenCommonConfig.CHAINSWORD_OVERHEAT_DURATION_TICKS.get();
        if (grindStart == null || max <= 0) {
            return 0.0F;
        }
        long elapsed = player.level().getGameTime() - grindStart;
        return Math.min(1.0F, (float) elapsed / (float) max);
    }
}
