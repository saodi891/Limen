package com.moonlightc.limen.item;

import com.moonlightc.limen.Limen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * "Flame Chasing Dagger" (斥火刀把) — a late-game test crossbow. Melee strikes immediately carve away half the
 * target's current health, and any bolt fired from it ignores armour; both go through {@link #FLAME_DAGGER} so
 * armour points never apply (see {@code data/minecraft/tags/damage_type/bypasses_armor.json}). The ranged
 * armour-bypass itself is wired in {@code FlameChasingDaggerEvents}.
 */
public class FlameChasingDaggerItem extends CrossbowItem {
    public static final ResourceKey<DamageType> FLAME_DAGGER =
            ResourceKey.create(Registries.DAMAGE_TYPE, Limen.id("flame_dagger"));

    private static final float MELEE_HEALTH_FRACTION = 0.5F;

    public FlameChasingDaggerItem(Properties properties) {
        super(properties);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return stack -> stack.getItem() instanceof ArrowItem;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> stack.getItem() instanceof ArrowItem || stack.is(Items.FIREWORK_ROCKET);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.level().isClientSide) {
            float cut = target.getHealth() * MELEE_HEALTH_FRACTION;
            if (cut > 0.0F) {
                target.invulnerableTime = 0;
                target.hurt(flameDaggerSource(target, attacker, attacker), cut);
            }
        }
        return true;
    }

    /** Armour-ignoring damage source for both melee and ranged hits of this weapon. */
    public static DamageSource flameDaggerSource(Entity context, Entity direct, Entity causing) {
        return new DamageSource(context.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FLAME_DAGGER), direct, causing);
    }
}
