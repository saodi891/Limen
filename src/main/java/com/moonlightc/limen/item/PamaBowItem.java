package com.moonlightc.limen.item;

import com.moonlightc.limen.entity.projectile.PamaTridentEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The "shock bow" (震撼弓): draws like a bow but fires a {@link PamaTridentEntity} instead of arrows, so it
 * needs no ammo. A full-charge shot leaves at twice a vanilla trident's speed, matching the projectile spec.
 */
public class PamaBowItem extends BowItem {
    private static final float MAX_LAUNCH_SPEED = 5.0F;

    public PamaBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // No ammo required: always allow drawing.
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int charge = this.getUseDuration(stack) - timeLeft;
        float power = getPowerForTime(charge);
        if (power < 0.1F) {
            return;
        }

        if (!level.isClientSide) {
            PamaTridentEntity trident = new PamaTridentEntity(level, player, new ItemStack(this));
            trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * MAX_LAUNCH_SPEED, 1.0F);
            if (player.getAbilities().instabuild) {
                trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            stack.hurtAndBreak(1, player, user -> user.broadcastBreakEvent(entity.getUsedItemHand()));
            level.addFreshEntity(trident);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }
}
