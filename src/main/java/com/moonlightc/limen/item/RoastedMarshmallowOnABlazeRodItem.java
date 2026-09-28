package com.moonlightc.limen.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * Toasted blaze-rod marshmallow: hands back its blaze rod once eaten, and burns what it hits
 * on the same terms as the raw {@link MarshmallowOnABlazeRodItem}.
 */
public class RoastedMarshmallowOnABlazeRodItem extends RoastedMarshmallowItem {
    public RoastedMarshmallowOnABlazeRodItem(Properties properties) {
        super(Items.BLAZE_ROD, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.setSecondsOnFire(MarshmallowOnABlazeRodItem.BURN_SECONDS);
        return super.hurtEnemy(stack, target, attacker);
    }
}
