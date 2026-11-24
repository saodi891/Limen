package com.moonlightc.limen.api.tag;

import com.moonlightc.limen.Limen;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class LimenEntityTypeTags {
    public static final TagKey<EntityType<?>> REDSTONE_MACHINES = TagKey.create(Registries.ENTITY_TYPE, Limen.id("redstone_machines"));
    public static final TagKey<EntityType<?>> SPELL_CONSTRUCTS = TagKey.create(Registries.ENTITY_TYPE, Limen.id("spell_constructs"));
    public static final TagKey<EntityType<?>> ILLAGER_FACTION = TagKey.create(Registries.ENTITY_TYPE, Limen.id("illager_faction"));
    public static final TagKey<EntityType<?>> GHOSTS = TagKey.create(Registries.ENTITY_TYPE, Limen.id("ghosts"));

    private LimenEntityTypeTags() {
    }
}
