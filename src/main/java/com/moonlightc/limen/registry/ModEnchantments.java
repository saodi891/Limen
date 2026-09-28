package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.enchantment.ErosionCurseEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Limen.MODID);

    public static final RegistryObject<Enchantment> EROSION_CURSE = ENCHANTMENTS.register(
            "erosion_curse",
            ErosionCurseEnchantment::new
    );

    private ModEnchantments() {
    }

    public static void register(IEventBus modBus) {
        ENCHANTMENTS.register(modBus);
    }
}
