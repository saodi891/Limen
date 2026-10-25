package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Grants one enchantment point per level gained once the player is past level 20. Levelling 19→20
 * yields nothing; every level reached beyond 20 (21, 22, …) hands over a single
 * {@link ModItems#ENCHANTMENT_POINTS} item, which the campfire enchant option spends.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class EnchantmentPointEvents {
    private static final int THRESHOLD = 20;

    private EnchantmentPointEvents() {
    }

    @SubscribeEvent
    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        int delta = event.getLevels();
        if (delta <= 0 || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The event fires before the delta is applied, so experienceLevel is still the old level here.
        int oldLevel = player.experienceLevel;
        int newLevel = oldLevel + delta;
        int points = newLevel - Math.max(oldLevel, THRESHOLD);
        if (points <= 0) {
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModItems.ENCHANTMENT_POINTS.get(), points));
    }
}
