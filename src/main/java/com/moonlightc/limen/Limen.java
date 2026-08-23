package com.moonlightc.limen;

import com.mojang.logging.LogUtils;
import com.moonlightc.limen.config.ModConfigs;
import com.moonlightc.limen.registry.ModBlocks;
import com.moonlightc.limen.registry.ModCreativeTabs;
import com.moonlightc.limen.registry.ModEntities;
import com.moonlightc.limen.registry.ModItems;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import software.bernie.geckolib.GeckoLib;

@Mod(Limen.MODID)
public final class Limen {
    public static final String MODID = "limen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Limen() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        ModBlocks.register(modBus);
        ModEntities.register(modBus);
        ModMobEffects.register(modBus);
        ModCreativeTabs.register(modBus);
        ModConfigs.register();

        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        GeckoLib.initialize();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Limen common setup complete.");
    }
}
