package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMobEffects {
    private static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Limen.MODID);

    public static final RegistryObject<MobEffect> SHOCKED = MOB_EFFECTS.register(
            "shocked",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6B0F16) {
            }
    );

    private ModMobEffects() {
    }

    public static void register(IEventBus modBus) {
        MOB_EFFECTS.register(modBus);
    }
}
