package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.ChainswordItem;
import com.moonlightc.limen.network.ChainswordHoldPacket;
import com.moonlightc.limen.network.ModNetworking;
import com.moonlightc.limen.sound.ChainswordGrindSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches the attack key while a chainsword is held and reports state changes to the server, which
 * owns the sustained-attack damage. Only edges are sent, so this is a handful of packets per swing.
 * Also owns the looping grind sound, the overheat flame particles and the grinding-player set used
 * by the sawing pose.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ClientChainswordEvents {
    private static boolean lastReportedHolding;
    /** Latest heat fraction (0..1) reported by the server; drives the cooldown-style overlay. */
    private static float heatProgress;
    /** Entity ids of players currently grinding, so their arm pose can be rendered. */
    private static final Set<Integer> GRINDING = ConcurrentHashMap.newKeySet();
    /** Looping grind sound per grinding player, stopped when they stop grinding. */
    private static final Map<Integer, ChainswordGrindSoundInstance> GRIND_SOUNDS = new ConcurrentHashMap<>();

    private ClientChainswordEvents() {
    }

    public static void setHeatProgress(float progress) {
        heatProgress = Math.max(0.0F, Math.min(1.0F, progress));
    }

    public static float getHeatProgress() {
        return heatProgress;
    }

    public static void setGrinding(int playerId, boolean grinding) {
        if (grinding) {
            GRINDING.add(playerId);
            startGrindSound(playerId);
        } else {
            GRINDING.remove(playerId);
            stopGrindSound(playerId);
        }
    }

    public static boolean isGrinding(Player player) {
        return GRINDING.contains(player.getId());
    }

    /**
     * Starts the looping saw sound for a player. The instance keeps playing until the server says
     * grinding stopped (trigger released or overheat), so the loop is not restarted per settlement.
     */
    private static void startGrindSound(int playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || GRIND_SOUNDS.containsKey(playerId)) {
            return;
        }
        if (!(minecraft.level.getEntity(playerId) instanceof Player player)) {
            return;
        }

        ChainswordGrindSoundInstance instance = new ChainswordGrindSoundInstance(player);
        GRIND_SOUNDS.put(playerId, instance);
        minecraft.getSoundManager().play(instance);
    }

    private static void stopGrindSound(int playerId) {
        ChainswordGrindSoundInstance instance = GRIND_SOUNDS.remove(playerId);
        if (instance != null) {
            instance.requestStop();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reportIfChanged(false);
            clearAllGrindSounds();
            return;
        }

        boolean chainswordInHand = player.getMainHandItem().getItem() instanceof ChainswordItem;
        boolean holding = minecraft.options.keyAttack.isDown()
                && minecraft.screen == null
                && chainswordInHand;
        reportIfChanged(holding);

        if (!chainswordInHand || !holding) {
            heatProgress = 0.0F;
        }

        if (chainswordInHand && player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())) {
            spawnOverheatParticles(player);
        }
    }

    private static void clearAllGrindSounds() {
        GRINDING.clear();
        GRIND_SOUNDS.values().forEach(ChainswordGrindSoundInstance::requestStop);
        GRIND_SOUNDS.clear();
    }

    /** Flame particles around the player's hand while the weapon is overheated. */
    private static void spawnOverheatParticles(LocalPlayer player) {
        if (player.tickCount % 2 != 0) {
            return;
        }

        for (int i = 0; i < 2; i++) {
            double x = player.getX() + (player.getRandom().nextDouble() - 0.5D) * 0.9D;
            double y = player.getY() + player.getBbHeight() * 0.75D + (player.getRandom().nextDouble() - 0.5D) * 0.4D;
            double z = player.getZ() + (player.getRandom().nextDouble() - 0.5D) * 0.9D;
            player.level().addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.02D, 0.0D);
        }
    }

    private static void reportIfChanged(boolean holding) {
        if (holding == lastReportedHolding) {
            return;
        }
        lastReportedHolding = holding;
        ModNetworking.CHANNEL.sendToServer(new ChainswordHoldPacket(holding));
    }
}
