package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.block.entity.ArtisanBoxBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Limen.MODID);

    public static final RegistryObject<BlockEntityType<ArtisanBoxBlockEntity>> ARTISAN_BOX = BLOCK_ENTITIES.register(
            "artisan_box",
            () -> BlockEntityType.Builder.of(ArtisanBoxBlockEntity::new, ModBlocks.ARTISAN_BOX.get()).build(null)
    );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
