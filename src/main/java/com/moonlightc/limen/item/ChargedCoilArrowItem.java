package com.moonlightc.limen.item;

import com.moonlightc.limen.entity.projectile.ChargedCoilArrowEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ChargedCoilArrowItem extends ArrowItem {
    public ChargedCoilArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
        ChargedCoilArrowEntity arrow = new ChargedCoilArrowEntity(level, shooter);
        arrow.setBaseDamage(2.5D);
        return arrow;
    }
}
