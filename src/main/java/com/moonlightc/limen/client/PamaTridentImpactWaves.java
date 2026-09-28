package com.moonlightc.limen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-only registry of short-lived PAMA trident impact waves. Each wave stores only its world centre and
 * the client game tick it began on; {@link com.moonlightc.limen.event.ClientForgeEvents} interpolates and
 * draws the expanding octahedron. Waves self-expire and the whole list is dropped when the level changes.
 */
public final class PamaTridentImpactWaves {
    /** Total wave lifetime in ticks. */
    public static final int DURATION_TICKS = 9;
    /** Defensive cap so a burst of packets cannot grow the list without bound. */
    private static final int MAX_WAVES = 64;

    private static final List<Wave> WAVES = new ArrayList<>();

    private PamaTridentImpactWaves() {
    }

    public static void add(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (WAVES.size() >= MAX_WAVES) {
            WAVES.remove(0);
        }
        WAVES.add(new Wave(new Vec3(x, y, z), minecraft.level.getGameTime()));
    }

    public static void clear() {
        WAVES.clear();
    }

    /** Removes expired waves and returns the live ones for rendering. */
    public static List<Wave> active(long gameTime) {
        Iterator<Wave> iterator = WAVES.iterator();
        while (iterator.hasNext()) {
            if (gameTime - iterator.next().startTick >= DURATION_TICKS) {
                iterator.remove();
            }
        }
        return WAVES;
    }

    public record Wave(Vec3 center, long startTick) {
    }
}
