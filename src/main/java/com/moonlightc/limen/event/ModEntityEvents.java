package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.AutonomousSpiderEntity;
import com.moonlightc.limen.entity.monster.EniacEntity;
import com.moonlightc.limen.entity.monster.PamaDetectorEntity;
import com.moonlightc.limen.entity.monster.PamaEntity;
import com.moonlightc.limen.entity.monster.SilkBabyEntity;
import com.moonlightc.limen.entity.monster.SkeletonChippedEntity;
import com.moonlightc.limen.entity.monster.ZombieChippedEntity;
import com.moonlightc.limen.entity.monster.TowerGuardEntity;
import com.moonlightc.limen.entity.monster.TowerGhostEntity;
import com.moonlightc.limen.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Limen.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityEvents {
    private ModEntityEvents() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.AUTONOMOUS_SPIDER.get(), AutonomousSpiderEntity.createAttributes().build());
        event.put(ModEntities.SILK_BABY.get(), SilkBabyEntity.createAttributes().build());
        event.put(ModEntities.ENIAC.get(), EniacEntity.createAttributes().build());
        event.put(ModEntities.TOWER_GUARD.get(), TowerGuardEntity.createAttributes().build());
        event.put(ModEntities.TOWER_GHOST.get(), TowerGhostEntity.createAttributes().build());
        event.put(ModEntities.PAMA_DETECTOR.get(), PamaDetectorEntity.createAttributes().build());
        event.put(ModEntities.PAMA.get(), PamaEntity.createAttributes().build());
        event.put(ModEntities.SKELETON_CHIPPED.get(), SkeletonChippedEntity.createAttributes().build());
        event.put(ModEntities.ZOMBIE_CHIPPED.get(), ZombieChippedEntity.createAttributes().build());
    }
}
