package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.AutonomousSpiderEntity;
import com.moonlightc.limen.entity.monster.EniacEntity;
import com.moonlightc.limen.entity.monster.PamaDetectorEntity;
import com.moonlightc.limen.entity.monster.SilkBabyEntity;
import com.moonlightc.limen.entity.monster.SkeletonChippedEntity;
import com.moonlightc.limen.entity.monster.ZombieChippedEntity;
import com.moonlightc.limen.entity.misc.TotemOfRelinkEntity;
import com.moonlightc.limen.entity.projectile.ChargedCoilArrowEntity;
import com.moonlightc.limen.entity.projectile.CoilFieldEntity;
import com.moonlightc.limen.entity.projectile.PamaEnderPearlEntity;
import com.moonlightc.limen.entity.projectile.PamaTridentEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Limen.MODID);

    public static final RegistryObject<EntityType<PamaDetectorEntity>> PAMA_DETECTOR = ENTITIES.register(
            "pama_detector",
            () -> EntityType.Builder.of(PamaDetectorEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(8)
                    .build(Limen.id("pama_detector").toString())
    );

    public static final RegistryObject<EntityType<ChargedCoilArrowEntity>> CHARGED_COIL_ARROW = ENTITIES.register(
            "charged_coil_arrow",
            () -> EntityType.Builder.<ChargedCoilArrowEntity>of(ChargedCoilArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(Limen.id("charged_coil_arrow").toString())
    );

    public static final RegistryObject<EntityType<PamaTridentEntity>> PAMA_TRIDENT = ENTITIES.register(
            "pama_trident",
            () -> EntityType.Builder.<PamaTridentEntity>of(PamaTridentEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build(Limen.id("pama_trident").toString())
    );

    public static final RegistryObject<EntityType<PamaEnderPearlEntity>> PAMA_ENDER_PEARL = ENTITIES.register(
            "pama_ender_pearl",
            () -> EntityType.Builder.<PamaEnderPearlEntity>of(PamaEnderPearlEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(Limen.id("pama_ender_pearl").toString())
    );

    public static final RegistryObject<EntityType<CoilFieldEntity>> COIL_FIELD = ENTITIES.register(
            "coil_field",
            () -> EntityType.Builder.<CoilFieldEntity>of(CoilFieldEntity::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(Limen.id("coil_field").toString())
    );

    public static final RegistryObject<EntityType<AutonomousSpiderEntity>> AUTONOMOUS_SPIDER = ENTITIES.register(
            "autonomous_spider",
            () -> EntityType.Builder.of(AutonomousSpiderEntity::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F)
                    .clientTrackingRange(8)
                    .build(Limen.id("autonomous_spider").toString())
    );

    public static final RegistryObject<EntityType<SilkBabyEntity>> SILK_BABY = ENTITIES.register(
            "silk_baby",
            () -> EntityType.Builder.of(SilkBabyEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.6F)
                    .clientTrackingRange(8)
                    .build(Limen.id("silk_baby").toString())
    );

    public static final RegistryObject<EntityType<EniacEntity>> ENIAC = ENTITIES.register(
            "eniac",
            () -> EntityType.Builder.of(EniacEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .build(Limen.id("eniac").toString())
    );

    public static final RegistryObject<EntityType<ZombieChippedEntity>> ZOMBIE_CHIPPED = ENTITIES.register(
            "zombie_chipped",
            () -> EntityType.Builder.of(ZombieChippedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(Limen.id("zombie_chipped").toString())
    );

    public static final RegistryObject<EntityType<SkeletonChippedEntity>> SKELETON_CHIPPED = ENTITIES.register(
            "skeleton_chipped",
            () -> EntityType.Builder.of(SkeletonChippedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .clientTrackingRange(8)
                    .build(Limen.id("skeleton_chipped").toString())
    );

    public static final RegistryObject<EntityType<TotemOfRelinkEntity>> TOTEM_OF_RELINK = ENTITIES.register(
            "totem_of_relink",
            () -> EntityType.Builder.<TotemOfRelinkEntity>of(TotemOfRelinkEntity::new, MobCategory.MISC)
                    .sized(0.5F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(Limen.id("totem_of_relink").toString())
    );

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
