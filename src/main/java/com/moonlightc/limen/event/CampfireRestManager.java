package com.moonlightc.limen.event;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have chosen to rest at a campfire and skips the night once enough of them have,
 * mirroring vanilla bed sleeping. Pressing any hub button other than Exit adds the player to the resting
 * set (a vote to pass the night); the night only skips when the resting players plus any players actually
 * asleep in beds meet the {@code playersSleepingPercentage} threshold, so the campfire and bed mechanics
 * stack toward the same requirement instead of each acting alone.
 *
 * <p>Votes are per-dimension and last for the current night: they expire at dawn, and drop for players who
 * leave the dimension. State is intentionally in-memory only — a resting vote is not worth persisting.
 */
public final class CampfireRestManager {
    private static final Map<ResourceKey<Level>, Set<UUID>> RESTING = new HashMap<>();

    private CampfireRestManager() {
    }

    /** Records the player as resting (a vote to skip the night) and broadcasts the current progress. */
    public static void addResting(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Night-skipping only makes sense in the Overworld — you cannot sleep in the Nether, so a rest there
        // still grants its per-player reward but never votes to fast-forward time.
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        Set<UUID> resting = RESTING.computeIfAbsent(level.dimension(), key -> new HashSet<>());
        resting.add(player.getUUID());

        int active = 0;
        int accounted = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            active++;
            if (resting.contains(p.getUUID()) || p.isSleeping()) {
                accounted++;
            }
        }
        int needed = sleepersNeeded(level, active);
        if (accounted < needed) {
            Component message = Component.translatable("message.limen.campfire_resting", accounted, needed);
            for (ServerPlayer p : level.players()) {
                p.displayClientMessage(message, true);
            }
        }
    }

    /** Per-level server tick: skip to dawn if enough players are resting or asleep; otherwise age out votes. */
    public static void tick(ServerLevel level) {
        Set<UUID> resting = RESTING.get(level.dimension());
        if (resting == null || resting.isEmpty()) {
            return;
        }

        long time = level.getDayTime() % 24000L;
        if (time < CampfireRoastEvents.NIGHT_START || time > CampfireRoastEvents.NIGHT_END) {
            resting.clear();
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            return;
        }

        int active = 0;
        int accounted = 0;
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            active++;
            boolean rests = resting.contains(player.getUUID());
            if (rests) {
                present.add(player.getUUID());
            }
            if (rests || player.isSleeping()) {
                accounted++;
            }
        }
        // Drop votes from players who logged out or left this dimension.
        resting.retainAll(present);
        if (resting.isEmpty()) {
            return;
        }

        if (accounted < sleepersNeeded(level, active)) {
            return;
        }

        long next = level.getDayTime() + 24000L;
        level.setDayTime(next - next % 24000L);
        level.setWeatherParameters(0, 0, false, false);
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping()) {
                player.stopSleepInBed(false, false);
            }
            if (resting.contains(player.getUUID())) {
                player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            }
        }
        resting.clear();
    }

    /** Vanilla {@code SleepStatus} rule: at least one, otherwise the configured percentage of active players. */
    private static int sleepersNeeded(ServerLevel level, int active) {
        int percentage = level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        return Math.max(1, Mth.ceil(active * percentage / 100.0F));
    }
}
