package com.moonlightc.limen.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public final class PolearmUseHelper {
    private PolearmUseHelper() {
    }

    public static boolean hasOffhandUseAction(Player player) {
        ItemStack offhand = player.getOffhandItem();
        return !offhand.isEmpty()
                && (offhand.getUseAnimation() != UseAnim.NONE || offhand.getUseDuration() > 0);
    }
}
