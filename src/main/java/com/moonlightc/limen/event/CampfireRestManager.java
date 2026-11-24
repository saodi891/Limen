package com.moonlightc.limen.event;

import com.moonlightc.limen.inventory.CampfireHubMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * Handles multiplayer night skipping for campfire use and beds. Campfire users are excluded from the
 * vanilla bed-sleep percentage; the night skips when the remaining eligible players meet that threshold,
 * or when every active Overworld player has used a campfire.
 */
public final class CampfireRestManager {
    private CampfireRestManager() {
    }

    /** Reports campfire-use progress after a player rests or completes a campfire enhancement. */
    public static void onCampfireUsed(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        int used = 0;
        int active = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            active++;
            if (CampfireHubMenu.hasSpentTonight(p)) {
                used++;
            }
        }
        if (used < active) {
            Component message = Component.translatable("message.limen.campfire_resting", used, active);
            for (ServerPlayer p : level.players()) {
                p.displayClientMessage(message, true);
            }
        }
    }

    /** Per-level server tick: evaluate eligible bed sleepers and the all-campfire-users condition. */
    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        long time = level.getDayTime() % 24000L;
        if (time < CampfireRoastEvents.NIGHT_START || time > CampfireRoastEvents.NIGHT_END) {
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            return;
        }

        int active = 0;
        int campfireUsers = 0;
        int eligibleForBed = 0;
        int sleeping = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            active++;
            if (CampfireHubMenu.hasSpentTonight(player)) {
                campfireUsers++;
            } else {
                eligibleForBed++;
                if (player.isSleeping()) {
                    sleeping++;
                }
            }
        }
        if (active == 0) {
            return;
        }

        boolean everyoneUsedCampfire = campfireUsers == active;
        boolean enoughBedSleepers = eligibleForBed > 0
                && sleeping >= sleepersNeeded(level, eligibleForBed);
        if (!everyoneUsedCampfire && !enoughBedSleepers) {
            return;
        }

        long next = level.getDayTime() + 24000L;
        level.setDayTime(next - next % 24000L);
        // Deliberately do NOT clear the weather: a campfire rest only skips the night, it cannot skip a storm.
        // Any ongoing rain or thunder carries into the new day (unlike vanilla bed sleep, which clears it).
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping()) {
                player.stopSleepInBed(false, false);
            }
            if (CampfireHubMenu.hasSpentTonight(player)) {
                player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            }
        }
    }

    /** Vanilla percentage rule, applied to players who have not already used a campfire tonight. */
    private static int sleepersNeeded(ServerLevel level, int active) {
        int percentage = level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        return Math.max(1, Mth.ceil(active * percentage / 100.0F));
    }
}
