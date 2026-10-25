package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.FlameChasingDaggerItem;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes every bolt fired from the Flame Chasing Dagger ignore armour. Arrows that spawn while the shooter holds
 * the dagger are tagged on join, then on hit their damage is re-applied through the armour-bypassing
 * {@code limen:flame_dagger} source carrying the same raw amount.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class FlameChasingDaggerEvents {
    private static final String SHOT_TAG = "LimenFlameDaggerShot";

    private FlameChasingDaggerEvents() {
    }

    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (arrow.getOwner() instanceof LivingEntity shooter
                && shooter.getMainHandItem().is(ModItems.FLAME_CHASING_DAGGER.get())) {
            arrow.getPersistentData().putBoolean(SHOT_TAG, true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if ("flame_dagger".equals(event.getSource().getMsgId())) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
                || !arrow.getPersistentData().getBoolean(SHOT_TAG)) {
            return;
        }
        LivingEntity target = event.getEntity();
        float amount = event.getAmount();
        event.setCanceled(true);
        target.hurt(FlameChasingDaggerItem.flameDaggerSource(target, arrow, arrow.getOwner()), amount);
    }
}
