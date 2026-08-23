package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class DeflectionShieldEvents {
    private static final double DEFLECT_RANGE = 5.0D;
    private static final double TARGET_RANGE = 18.0D;
    private static final String DEFLECTED_TAG = "LimenDeflectedByShield";

    private DeflectionShieldEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide || !player.isAlive() || !player.isUsingItem()) {
            return;
        }

        ItemStack stack = player.getUseItem();
        if (!stack.is(ModItems.DEFLECTION_SHIELD.get())) {
            return;
        }

        Vec3 look = player.getLookAngle().normalize();
        AABB scanBox = player.getBoundingBox().inflate(DEFLECT_RANGE).expandTowards(look.scale(DEFLECT_RANGE));
        List<AbstractArrow> arrows = player.level().getEntitiesOfClass(AbstractArrow.class, scanBox, arrow -> shouldDeflect(player, arrow, look));
        for (AbstractArrow arrow : arrows) {
            redirectArrow(player, arrow);
        }
    }

    private static boolean shouldDeflect(Player player, AbstractArrow arrow, Vec3 look) {
        if (!arrow.isAlive() || arrow.getPersistentData().getBoolean(DEFLECTED_TAG)) {
            return false;
        }

        Vec3 toArrow = arrow.position().subtract(player.position());
        if (toArrow.lengthSqr() < 0.25D) {
            return false;
        }

        Vec3 arrowMotion = arrow.getDeltaMovement();
        if (arrowMotion.lengthSqr() < 0.01D) {
            return false;
        }

        double inFront = toArrow.normalize().dot(look);
        double incoming = arrowMotion.normalize().dot(look);
        return inFront > 0.2D && incoming < -0.15D;
    }

    private static void redirectArrow(Player player, AbstractArrow arrow) {
        LivingEntity target = findTarget(player, arrow);
        if (target == null) {
            return;
        }

        Vec3 targetPos = target.getEyePosition().add(0.0D, -0.15D, 0.0D);
        Vec3 direction = targetPos.subtract(arrow.position());
        if (direction.lengthSqr() < 0.01D) {
            return;
        }

        double speed = Math.max(arrow.getDeltaMovement().length(), 2.8D);
        Vec3 newMotion = direction.normalize().scale(speed + 0.7D);

        arrow.setDeltaMovement(newMotion);
        arrow.setOwner(player);
        arrow.setYRot((float) (Mth.atan2(newMotion.x, newMotion.z) * (180F / (float) Math.PI)));
        arrow.setXRot((float) (Mth.atan2(newMotion.y, newMotion.horizontalDistance()) * (180F / (float) Math.PI)));
        arrow.getPersistentData().putBoolean(DEFLECTED_TAG, true);
        arrow.hasImpulse = true;
    }

    private static LivingEntity findTarget(Player player, AbstractArrow arrow) {
        AABB searchBox = arrow.getBoundingBox().inflate(TARGET_RANGE);
        return player.level().getEntitiesOfClass(LivingEntity.class, searchBox, target -> isValidTarget(player, target))
                .stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(arrow)))
                .orElse(null);
    }

    private static boolean isValidTarget(Player holder, LivingEntity target) {
        if (target == holder || !target.isAlive() || target.isAlliedTo(holder) || holder.isAlliedTo(target)) {
            return false;
        }

        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }

        if (target instanceof Mob mob) {
            return mob.getTarget() == holder || mob.getLastHurtByMob() == holder;
        }

        return false;
    }
}
