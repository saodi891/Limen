package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.block.StructureVoidBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Limen.MODID);

    public static final RegistryObject<Block> AUTONOMOUS_SPIDER_REMAINS = BLOCKS.register(
            "autonomous_spider_remains",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.METAL))
    );

    public static final RegistryObject<Block> STRUCTURE_VOID_BLOCK = BLOCKS.register(
            "structure_void_block",
            () -> new StructureVoidBlock(BlockBehaviour.Properties.copy(Blocks.STRUCTURE_VOID)
                    .noCollission()
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.GLASS))
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
