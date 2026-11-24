package com.moonlightc.limen.registry;

import com.google.common.collect.ImmutableSet;
import com.moonlightc.limen.Limen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public final class ModVillagers {
    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, Limen.MODID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, Limen.MODID);

    public static final RegistryObject<PoiType> MECHANIC_WORKSTATION = POI_TYPES.register(
            "mechanic_workstation",
            () -> new PoiType(getTableSawStates(), 1, 1)
    );

    public static final RegistryObject<VillagerProfession> MECHANIC = PROFESSIONS.register(
            "mechanic",
            () -> new VillagerProfession(
                    "mechanic",
                    holder -> holder.is(MECHANIC_WORKSTATION.getKey()),
                    holder -> holder.is(MECHANIC_WORKSTATION.getKey()),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_TOOLSMITH
            )
    );

    private ModVillagers() {
    }

    private static Set<BlockState> getTableSawStates() {
        return ImmutableSet.copyOf(ModBlocks.TABLE_SAW.get().getStateDefinition().getPossibleStates());
    }

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
    }
}
