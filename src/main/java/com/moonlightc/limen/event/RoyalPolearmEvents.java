package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.registry.ModItems;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class RoyalPolearmEvents {
    private static final String ROYAL_POLEARM_TAG = "LimenRoyalPolearm";
    private static final String CHARGE_START_TICK = "ChargeStartTick";
    private static final String CHARGE_HIT_TIMES = "ChargeHitTimes";
    private static final String FEEDBACK_UNTIL_TICK = "FeedbackUntilTick";
    private static final double ATTACK_START_DISTANCE = 0.0D;
    private static final double CHARGE_ATTACK_START_DISTANCE = 0.0D;
    private static final double ATTACK_END_DISTANCE = 4.5D;
    private static final double STAB_BOX_EXPANSION = 0.25D;
    private static final double CHARGE_BOX_EXPANSION = 0.125D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double MIN_CHARGE_FORWARD_SPEED = 0.02D;
    private static final double POLEARM_SWEEP_RADIUS = 0.55D;
    private static final int POLEARM_SWEEP_STEPS = 8;
    private static final int CHARGE_PREP_TICKS = 10;
    private static final int CHARGE_FEEDBACK_TICKS = 12;
    private static final int CHARGE_REHIT_TICKS = 5;
    private static final double CHARGE_KNOCKBACK_SPEED = 5.1D / TICKS_PER_SECOND;
    private static final double CHARGE_DISMOUNT_SPEED = 7.5D / TICKS_PER_SECOND;
    private static final float CHARGE_KNOCKBACK_STRENGTH = 1.15F;
    private static final float STAB_DAMAGE = 9.0F;
    private static final float POWERED_SHOCK_DAMAGE_THRESHOLD = 5.0F;
    private static final int POWERED_SHOCK_TICKS = 3 * 20;

    private RoyalPolearmEvents() {
    }

    private static boolean isPolearm(ItemStack stack) {
        return stack.is(ModItems.ROYAL_POLEARM.get()) || stack.is(ModItems.POWERED_POLEARM.get());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!isPolearm(stack)) {
            return;
        }

        event.setCanceled(true);
        if (player.isUsingItem() || player.getAttackStrengthScale(0.5F) < 1.0F) {
            return;
        }

        List<LivingEntity> targets = findPolearmTargets(player, ATTACK_START_DISTANCE, ATTACK_END_DISTANCE, STAB_BOX_EXPANSION);
        if (targets.isEmpty()) {
            return;
        }

        for (LivingEntity target : targets) {
            if (applyStabHit(player, target, stack)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                }
            }
        }

        player.resetAttackStrengthTicker();
        player.causeFoodExhaustion(0.1F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7F, 1.15F);
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

        if (!player.isUsingItem() || !isPolearm(player.getUseItem())) {
            clearChargeState(player);
            return;
        }

        ItemStack stack = player.getUseItem();
        CompoundTag chargeTag = getChargeTag(player);
        long now = player.level().getGameTime();
        if (!chargeTag.contains(CHARGE_START_TICK)) {
            chargeTag.putLong(CHARGE_START_TICK, now);
        }

        int chargeTicks = (int) (now - chargeTag.getLong(CHARGE_START_TICK));
        if (chargeTicks < CHARGE_PREP_TICKS) {
            return;
        }

        double forwardSpeed = player.getDeltaMovement().dot(player.getLookAngle().normalize());
        if (forwardSpeed <= MIN_CHARGE_FORWARD_SPEED) {
            return;
        }

        List<LivingEntity> targets = findPolearmTargets(player, CHARGE_ATTACK_START_DISTANCE, ATTACK_END_DISTANCE, CHARGE_BOX_EXPANSION);
        if (targets.isEmpty()) {
            return;
        }

        boolean hitAny = false;
        for (LivingEntity target : targets) {
            if (applyChargeHit(player, target, stack, now)) {
                hitAny = true;
            }
        }

        if (!hitAny) {
            return;
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, player.getSoundSource(), 0.9F, 0.95F);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
    }

    private static boolean applyStabHit(Player player, LivingEntity target, ItemStack stack) {
        float damage = getStabDamage(player, target);
        if (!hurtWithPolearm(player, target, damage)) {
            return false;
        }

        stack.hurtAndBreak(1, player, living -> living.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    private static boolean applyChargeHit(Player player, LivingEntity target, ItemStack stack, long now) {
        if (!canChargeHit(player, target, now)) {
            return false;
        }

        Vec3 look = player.getLookAngle().normalize();
        double attackerSpeed = player.getDeltaMovement().dot(look);
        double relativeSpeed = Math.abs(player.getDeltaMovement().subtract(target.getDeltaMovement()).dot(look));

        float damage = Math.max(1.0F, Mth.ceil(Math.max(attackerSpeed, relativeSpeed) * TICKS_PER_SECOND));
        boolean harmed = hurtWithPolearm(player, target, damage);
        if (harmed) {
            maybeApplyElectricShock(target, stack, damage);
        }

        target.stopRiding();
        if (attackerSpeed > CHARGE_KNOCKBACK_SPEED) {
            Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
            if (horizontalLook.lengthSqr() > 1.0E-6D) {
                horizontalLook = horizontalLook.normalize();
                target.knockback(CHARGE_KNOCKBACK_STRENGTH, -horizontalLook.x, -horizontalLook.z);
            }
        }
        if (relativeSpeed > CHARGE_DISMOUNT_SPEED) {
            target.stopRiding();
        }

        markChargeHit(player, target, now);
        stack.hurtAndBreak(1, player, living -> living.broadcastBreakEvent(player.getUsedItemHand()));
        return harmed || attackerSpeed > CHARGE_KNOCKBACK_SPEED || relativeSpeed > CHARGE_DISMOUNT_SPEED;
    }

    private static float getStabDamage(Player player, LivingEntity target) {
        float enchantmentDamage = EnchantmentHelper.getDamageBonus(player.getMainHandItem(), target.getMobType());
        return STAB_DAMAGE + enchantmentDamage;
    }

    private static boolean hurtWithPolearm(Player player, LivingEntity target, float damage) {
        DamageSource damageSource = player.damageSources().playerAttack(player);
        if (!target.hurt(damageSource, damage)) {
            return false;
        }

        EnchantmentHelper.doPostHurtEffects(target, player);
        EnchantmentHelper.doPostDamageEffects(player, target);
        return true;
    }

    private static void maybeApplyElectricShock(LivingEntity target, ItemStack stack, float damage) {
        if (stack.is(ModItems.POWERED_POLEARM.get()) && damage >= POWERED_SHOCK_DAMAGE_THRESHOLD) {
            target.addEffect(new MobEffectInstance(ModMobEffects.SHOCKED.get(), POWERED_SHOCK_TICKS, 0, false, true));
        }
    }

    private static List<LivingEntity> findPolearmTargets(Player player, double startDistance, double endDistance, double boxExpansion) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 start = eyePosition.add(look.scale(startDistance));
        Vec3 end = eyePosition.add(look.scale(endDistance));
        Vec3 bodyPosition = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
        Vec3 bodyStart = bodyPosition.add(look.scale(startDistance));
        Vec3 bodyEnd = bodyPosition.add(look.scale(endDistance));
        AABB searchBox = new AABB(start, end)
                .minmax(new AABB(bodyStart, bodyEnd))
                .inflate(1.5D);

        List<LivingEntity> targets = new ArrayList<>(player.level().getEntitiesOfClass(LivingEntity.class, searchBox, living ->
                living.isAlive()
                        && living != player
                        && !living.isSpectator()
                        && isInFront(player, living, look)));

        targets.sort(Comparator.comparingDouble(player::distanceToSqr));
        targets.removeIf(target -> !intersectsPolearmLine(target, start, end, bodyStart, bodyEnd, boxExpansion));
        return targets;
    }

    private static boolean intersectsPolearmLine(LivingEntity target, Vec3 eyeStart, Vec3 eyeEnd, Vec3 bodyStart, Vec3 bodyEnd, double boxExpansion) {
        AABB attackBox = target.getBoundingBox()
                .inflate(boxExpansion)
                .expandTowards(target.getDeltaMovement());
        return intersectsSweptPolearmVolume(attackBox, eyeStart, eyeEnd)
                || intersectsSweptPolearmVolume(attackBox, bodyStart, bodyEnd);
    }

    private static boolean intersectsSweptPolearmVolume(AABB targetBox, Vec3 start, Vec3 end) {
        Vec3 step = end.subtract(start).scale(1.0D / POLEARM_SWEEP_STEPS);
        for (int i = 0; i <= POLEARM_SWEEP_STEPS; i++) {
            Vec3 point = start.add(step.scale(i));
            AABB sweepBox = new AABB(
                    point.x - POLEARM_SWEEP_RADIUS,
                    point.y - POLEARM_SWEEP_RADIUS,
                    point.z - POLEARM_SWEEP_RADIUS,
                    point.x + POLEARM_SWEEP_RADIUS,
                    point.y + POLEARM_SWEEP_RADIUS,
                    point.z + POLEARM_SWEEP_RADIUS
            );
            if (targetBox.intersects(sweepBox)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInFront(Player player, Entity target, Vec3 look) {
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (toTarget.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return look.dot(toTarget.normalize()) > 0.1D;
    }

    private static CompoundTag getChargeTag(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = root.getCompound(ROYAL_POLEARM_TAG);
        if (!root.contains(ROYAL_POLEARM_TAG)) {
            root.put(ROYAL_POLEARM_TAG, tag);
        }
        return tag;
    }

    private static void clearChargeState(Player player) {
        player.getPersistentData().remove(ROYAL_POLEARM_TAG);
    }

    private static boolean canChargeHit(Player player, LivingEntity target, long now) {
        CompoundTag hitTimes = getChargeTag(player).getCompound(CHARGE_HIT_TIMES);
        if (!hitTimes.contains(target.getStringUUID())) {
            return true;
        }
        long lastHitTick = hitTimes.getLong(target.getStringUUID());
        return now - lastHitTick >= CHARGE_REHIT_TICKS;
    }

    private static void markChargeHit(Player player, LivingEntity target, long now) {
        CompoundTag chargeTag = getChargeTag(player);
        CompoundTag hitTimes = chargeTag.getCompound(CHARGE_HIT_TIMES);
        hitTimes.putLong(target.getStringUUID(), now);
        chargeTag.put(CHARGE_HIT_TIMES, hitTimes);
        chargeTag.putLong(FEEDBACK_UNTIL_TICK, now + CHARGE_FEEDBACK_TICKS);
    }
}
