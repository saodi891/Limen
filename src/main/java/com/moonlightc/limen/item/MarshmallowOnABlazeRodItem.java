package com.moonlightc.limen.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Marshmallow on a blaze rod. Sets the victim on fire for two seconds when used to attack.
 *
 * <p>{@code setSecondsOnFire} routes through {@code ProtectionEnchantment.getFireAfterDampener},
 * so Fire Protection shortens the burn and fire-immune targets shed it on the next tick — the
 * "affected by fireproofing" part of the requirement needs no code of its own.
 */
public class MarshmallowOnABlazeRodItem extends Item {
    static final int BURN_SECONDS = 2;

    public MarshmallowOnABlazeRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.setSecondsOnFire(BURN_SECONDS);
        return super.hurtEnemy(stack, target, attacker);
    }
}
