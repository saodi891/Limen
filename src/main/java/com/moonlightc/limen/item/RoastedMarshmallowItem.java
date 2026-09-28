package com.moonlightc.limen.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A roasted marshmallow, which hands its skewer back once eaten.
 *
 * <p>Same shape as vanilla {@code BowlFoodItem}: the remainder replaces the emptied stack from
 * {@code finishUsingItem}, except in creative, where the original stack was never consumed and
 * returning the skewer would hand out a free one.
 */
public class RoastedMarshmallowItem extends Item {
    private final Item skewer;

    public RoastedMarshmallowItem(Item skewer, Properties properties) {
        super(properties);
        this.skewer = skewer;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack remainder = super.finishUsingItem(stack, level, entity);
        if (entity instanceof Player player && player.getAbilities().instabuild) {
            return remainder;
        }
        return new ItemStack(this.skewer);
    }
}
