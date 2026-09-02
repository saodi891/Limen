package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Limen.MODID);

    public static final RegistryObject<CreativeModeTab> LIMEN_TAB = CREATIVE_TABS.register(
            "limen",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.limen"))
                    .icon(() -> new ItemStack(ModItems.PAMA_DETECTOR_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.AUTONOMOUS_SPIDER_SPAWN_EGG.get());
                        output.accept(ModItems.PAMA_DETECTOR_SPAWN_EGG.get());
                        output.accept(ModItems.AUTONOMOUS_SPIDER_REMAINS.get());
                        output.accept(ModItems.STRUCTURE_VOID_BLOCK.get());
                        output.accept(ModItems.ARTISAN_BOX.get());
                        output.accept(ModItems.ARTISAN_HEART.get());
                        output.accept(ModItems.SKELETON_CHIPPED_SPAWN_EGG.get());
                        output.accept(ModItems.ZOMBIE_CHIPPED_SPAWN_EGG.get());
                        output.accept(ModItems.DAMAGED_CHIP.get());
                        output.accept(ModItems.CHARGED_COIL_ARROW.get());
                        output.accept(ModItems.ELECTROMAGNETIC_CROSSBOW.get());
                        output.accept(ModItems.MINDCONTROL_CHIP.get());
                        output.accept(ModItems.LEAVES_OF_GRASS.get());
                        output.accept(ModItems.POLEARM_COMBAT_TECHNIQUES.get());
                        output.accept(ModItems.ROYAL_POLEARM.get());
                        output.accept(ModItems.CHAINSWORD.get());
                        output.accept(ModItems.LIVING_SHIELD.get());
                        output.accept(ModItems.DEFLECTION_SHIELD.get());
                        output.accept(ModItems.ELECTROMAGNETIC_CROSSBOW_BLUEPRINT.get());
                        output.accept(ModItems.POWERED_POLEARM_BLUEPRINT.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        CREATIVE_TABS.register(modBus);
    }
}
