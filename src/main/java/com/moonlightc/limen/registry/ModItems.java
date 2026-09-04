package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.ChainswordItem;
import com.moonlightc.limen.item.ChargedCoilArrowItem;
import com.moonlightc.limen.item.ElectromagneticCrossbowItem;
import com.moonlightc.limen.item.DeflectionShieldItem;
import com.moonlightc.limen.item.LivingShieldItem;
import com.moonlightc.limen.item.MarshmallowOnABlazeRodItem;
import com.moonlightc.limen.item.PatchouliBookItem;
import com.moonlightc.limen.item.PolearmCombatTechniquesItem;
import com.moonlightc.limen.item.RoyalPolearmItem;
import com.moonlightc.limen.item.StructureVoidBlockItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Limen.MODID);

    public static final RegistryObject<Item> PAMA_DETECTOR_SPAWN_EGG = ITEMS.register(
            "pama_detector_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.PAMA_DETECTOR, 0x2B3038, 0xD94A4A, new Item.Properties())
    );

    public static final RegistryObject<Item> AUTONOMOUS_SPIDER_SPAWN_EGG = ITEMS.register(
            "autonomous_spider_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.AUTONOMOUS_SPIDER, 0x3C4046, 0xA61F2D, new Item.Properties())
    );

    public static final RegistryObject<Item> ZOMBIE_CHIPPED_SPAWN_EGG = ITEMS.register(
            "zombie_chipped_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ZOMBIE_CHIPPED, 0x4D6C52, 0xA71F2E, new Item.Properties())
    );

    public static final RegistryObject<Item> SKELETON_CHIPPED_SPAWN_EGG = ITEMS.register(
            "skeleton_chipped_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SKELETON_CHIPPED, 0xB7B8BA, 0x7F2020, new Item.Properties())
    );

    public static final RegistryObject<Item> DAMAGED_CHIP = ITEMS.register(
            "damaged_chip",
            () -> new Item(new Item.Properties())
    );

    public static final RegistryObject<Item> AUTONOMOUS_SPIDER_REMAINS = ITEMS.register(
            "autonomous_spider_remains",
            () -> new BlockItem(ModBlocks.AUTONOMOUS_SPIDER_REMAINS.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> STRUCTURE_VOID_BLOCK = ITEMS.register(
            "structure_void_block",
            () -> new StructureVoidBlockItem(ModBlocks.STRUCTURE_VOID_BLOCK.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> MINDCONTROL_CHIP = ITEMS.register(
            "mindcontrol_chip",
            () -> new Item(new Item.Properties())
    );

    public static final RegistryObject<Item> REDSTONE_MONSTER_HUNTER = ITEMS.register(
            "redstone_monster_hunter",
            () -> new Item(new Item.Properties())
    );

    public static final RegistryObject<Item> CHARGED_COIL_ARROW = ITEMS.register(
            "charged_coil_arrow",
            () -> new ChargedCoilArrowItem(new Item.Properties())
    );

    public static final RegistryObject<Item> ELECTROMAGNETIC_CROSSBOW = ITEMS.register(
            "electromagnetic_crossbow",
            () -> new ElectromagneticCrossbowItem(new Item.Properties().durability(768))
    );

    public static final RegistryObject<Item> LEAVES_OF_GRASS = ITEMS.register(
            "leaves_of_grass",
            () -> new PatchouliBookItem(new Item.Properties().stacksTo(1), Limen.id("leaves_of_grass"))
    );

    public static final RegistryObject<Item> POLEARM_COMBAT_TECHNIQUES = ITEMS.register(
            "polearm_combat_techniques",
            () -> new PolearmCombatTechniquesItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> ROYAL_POLEARM = ITEMS.register(
            "royal_polearm",
            () -> new RoyalPolearmItem(new Item.Properties().durability(812))
    );

    public static final RegistryObject<Item> LIVING_SHIELD = ITEMS.register(
            "living_shield",
            () -> new LivingShieldItem(new Item.Properties().durability(336))
    );

    public static final RegistryObject<Item> DEFLECTION_SHIELD = ITEMS.register(
            "deflection_shield",
            () -> new DeflectionShieldItem(new Item.Properties().durability(336))
    );

    public static final RegistryObject<Item> ARTISAN_BOX = ITEMS.register(
            "artisan_box",
            () -> new com.moonlightc.limen.item.ArtisanBoxItem(ModBlocks.ARTISAN_BOX.get(), new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> ARTISAN_HEART = ITEMS.register(
            "artisan_heart",
            () -> new com.moonlightc.limen.item.ArtisanHeartItem(new Item.Properties().stacksTo(1).fireResistant())
    );

    public static final RegistryObject<Item> CHAINSWORD = ITEMS.register(
            "chainsword",
            () -> new ChainswordItem(new Item.Properties())
    );

    /**
     * Nutrition 2 with saturation modifier 0.3, eaten in 16 ticks and grantable on a full hunger bar.
     *
     * <p>{@code fast()} is what makes it 16 ticks: vanilla's {@code Item.getUseDuration} returns a flat
     * 16 for fast food and 32 otherwise, with no way to name an arbitrary duration from
     * {@code Properties}. The speed effect is supplied lazily so the registry object is resolved when
     * the item is built rather than while this class is still loading.
     */
    public static final RegistryObject<Item> MARSHMALLOW = ITEMS.register(
            "marshmallow",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationMod(0.3F)
                    .fast()
                    .alwaysEat()
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0), 1.0F)
                    .build()))
    );

    public static final RegistryObject<Item> MARSHMALLOW_ONASTICK = ITEMS.register(
            "marshmallow_onastick",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> MARSHMALLOW_ONABLAZEROD = ITEMS.register(
            "marshmallow_onablazerod",
            () -> new MarshmallowOnABlazeRodItem(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> ELECTROMAGNETIC_CROSSBOW_BLUEPRINT = ITEMS.register(
            "electromagnetic_crossbow_blueprint",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> POWERED_POLEARM_BLUEPRINT = ITEMS.register(
            "powered_polearm_blueprint",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
