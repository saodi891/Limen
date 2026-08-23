package com.moonlightc.limen.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class LivingShieldItem extends BaseShieldLikeItem {
    private static final String LAST_ROAR_TICK = "LastRoarTick";
    public static final int ROAR_COOLDOWN_TICKS = 15 * 20;

    public LivingShieldItem(Properties properties) {
        super(properties);
    }

    public static boolean isRoarReady(ItemStack stack, long gameTime) {
        if (!(stack.getItem() instanceof LivingShieldItem)) {
            return false;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(LAST_ROAR_TICK)) {
            return true;
        }

        return gameTime - tag.getLong(LAST_ROAR_TICK) >= ROAR_COOLDOWN_TICKS;
    }

    public static void markRoarUsed(ItemStack stack, long gameTime) {
        stack.getOrCreateTag().putLong(LAST_ROAR_TICK, gameTime);
    }
}
