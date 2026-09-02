package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Limen.MODID);

    /** Plays once when the chainsword spins up at the start of a sustained attack. */
    public static final RegistryObject<SoundEvent> CHAINSWORD_START = register("chainsword_start");
    /** Plays on each grind settlement while sustained attack runs. */
    public static final RegistryObject<SoundEvent> CHAINSWORD_GRIND = register("chainsword_grind");
    /** Plays when the weapon overheats. */
    public static final RegistryObject<SoundEvent> CHAINSWORD_OVERHEAT = register("chainsword_overheat");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Limen.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
