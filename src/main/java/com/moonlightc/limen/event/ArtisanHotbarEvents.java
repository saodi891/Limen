package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.hotbar.ArtisanHeartLogic;
import com.moonlightc.limen.hotbar.ArtisanHotbarSync;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side rules for the artisan hotbar slots: deselect when the heart is lost or when vanilla
 * moves the selection out from under a mount, keep clients in sync, decide what death does to the
 * stored stacks, and redirect the offhand swap when a heart is linked.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class ArtisanHotbarEvents {
    /** Checking every tick would scan the inventory constantly; four times a second is responsive enough. */
    private static final int HEART_CHECK_INTERVAL_TICKS = 5;

    private ArtisanHotbarEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        if (hotbars == null) {
            return;
        }

        // A number key or a mod changing inventory.selected bypasses select(), which would leave the
        // artisan stack sitting in a slot the player is no longer holding.
        hotbars.reconcile(player);

        if (player.tickCount % HEART_CHECK_INTERVAL_TICKS == 0) {
            if (hotbars.hasArtisanSelected() && !ArtisanHeartLogic.hasHeart(player)) {
                hotbars.deselect(player);
            }
            // Keep every carried heart's stored-slot snapshot current, so taking one out shows what
            // the slots held at that moment.
            ArtisanHeartLogic.writeSlotsToHearts(player);
        }

        if (hotbars.isDirty()) {
            ArtisanHotbarSync.sync(player, hotbars);
        }
    }

    /**
     * Death has to deselect first: a mounted artisan stack is physically in the hotbar, and vanilla is
     * about to drop the hotbar. Deselecting puts the player's own stack back so vanilla drops that,
     * then the artisan slots are handled by the config.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ArtisanHotbars hotbars = ArtisanHeartLogic.getHotbars(player);
        if (hotbars == null) {
            return;
        }

        hotbars.deselect(player);

        boolean keepInventory = player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        if (keepInventory || LimenCommonConfig.ARTISAN_HOTBARS_KEEP_ON_DEATH.get()) {
            return;
        }
        hotbars.dropAll(player);
    }

    /**
     * Redirects the vanilla hand swap into a hotbar-to-box exchange when the offhand heart is linked.
     * The client normally suppresses its own swap packet, so this is the server's safety net for a
     * client that sent one anyway.
     */
    @SubscribeEvent
    public static void onSwapItems(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ArtisanHeartLogic.canSwapWithBox(player)) {
            return;
        }

        event.setCanceled(true);
        if (ArtisanHeartLogic.swapHotbarWithBox(player)) {
            ArtisanHotbarSync.sync(player);
        }
    }
}
