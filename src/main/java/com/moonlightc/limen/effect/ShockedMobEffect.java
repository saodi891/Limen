package com.moonlightc.limen.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * "Shocked" immobilizes its holder. The bulk of the enforcement lives in
 * {@link com.moonlightc.limen.event.ShockedEffectEvents} (velocity/AI) and
 * {@link com.moonlightc.limen.event.ClientShockedEffectEvents} (player input/FOV); the movement-speed
 * modifier here is what keeps mobs rooted at the attribute level. It is removed automatically when the
 * effect expires, so no manual restore is needed.
 */
public class ShockedMobEffect extends MobEffect {
    // Stable UUID for the movement-speed lock modifier.
    private static final String MOVEMENT_SPEED_MODIFIER_UUID = "5b9f0e2a-6c4d-4d2f-9a1e-8f7c1d3b2a10";

    public ShockedMobEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_MODIFIER_UUID,
                -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
