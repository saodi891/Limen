package com.moonlightc.limen.item;

import com.moonlightc.limen.entity.projectile.PamaTridentEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Charged trident item. Reuses vanilla {@link TridentItem} behaviour (spear use, riptide, durability,
 * enchantability) but overrides the throw so it launches a {@link PamaTridentEntity} at twice the vanilla
 * launch speed. This item never appears in a player's hand or inventory in normal play; it exists only as
 * the {@link ItemStack} carried by the projectile ENIAC fires, so it needs no custom held/icon renderer.
 */
public class PamaTridentItem extends TridentItem {
    /** Vanilla trident launch power is 2.5F; this projectile leaves at double that. */
    private static final float SHOOT_POWER = 5.0F;

    public PamaTridentItem(Properties properties) {
        super(properties);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int useTime = this.getUseDuration(stack) - timeLeft;
        if (useTime < 10) {
            return;
        }

        int riptide = EnchantmentHelper.getRiptide(stack);
        if (riptide > 0 && !player.isInWaterOrRain()) {
            return;
        }

        if (!level.isClientSide) {
            stack.hurtAndBreak(1, player, user -> user.broadcastBreakEvent(entity.getUsedItemHand()));
            if (riptide == 0) {
                PamaTridentEntity trident = new PamaTridentEntity(level, player, stack);
                trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                        SHOOT_POWER + riptide * 0.5F, 1.0F);
                if (player.getAbilities().instabuild) {
                    trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
                level.addFreshEntity(trident);
                level.playSound(null, trident, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    player.getInventory().removeItem(stack);
                }
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (riptide > 0) {
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            float x = -Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
            float y = -Mth.sin(pitch * Mth.DEG_TO_RAD);
            float z = Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
            float magnitude = Mth.sqrt(x * x + y * y + z * z);
            float scale = 3.0F * ((1.0F + riptide) / 4.0F);
            x *= scale / magnitude;
            y *= scale / magnitude;
            z *= scale / magnitude;
            player.push(x, y, z);
            player.startAutoSpinAttack(20);
            if (player.onGround()) {
                player.move(MoverType.SELF, new Vec3(0.0D, 1.1999999284744263D, 0.0D));
            }
            level.playSound(null, player,
                    riptide >= 3 ? SoundEvents.TRIDENT_RIPTIDE_3
                            : riptide == 2 ? SoundEvents.TRIDENT_RIPTIDE_2 : SoundEvents.TRIDENT_RIPTIDE_1,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
