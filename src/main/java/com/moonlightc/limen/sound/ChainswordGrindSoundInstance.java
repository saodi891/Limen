package com.moonlightc.limen.sound;

import com.moonlightc.limen.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Looping chainsword grind sound. Follows the grinding player and keeps playing until
 * {@link #requestStop()} is called (trigger released or the weapon overheats), rather than being
 * re-triggered on every damage settlement.
 */
public class ChainswordGrindSoundInstance extends AbstractTickableSoundInstance {
    private final Player player;
    private boolean stopRequested;

    public ChainswordGrindSoundInstance(Player player) {
        super(ModSounds.CHAINSWORD_GRIND.get(), SoundSource.PLAYERS, player.getRandom());
        this.player = player;
        this.looping = true;
        // No gap between loop iterations, so the saw sounds continuous.
        this.delay = 0;
        this.volume = 0.7F;
        this.pitch = 1.0F;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    public void requestStop() {
        this.stopRequested = true;
    }

    @Override
    public void tick() {
        if (this.stopRequested || !this.player.isAlive() || this.player.isRemoved()) {
            this.stop();
            return;
        }

        // Track the player so the saw is heard from wherever they are.
        this.x = this.player.getX();
        this.y = this.player.getY();
        this.z = this.player.getZ();
    }

    @Override
    public boolean canPlaySound() {
        return !this.player.isSilent();
    }
}
