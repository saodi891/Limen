package com.moonlightc.limen.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

public class ChargedBreadItem extends Item {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public ChargedBreadItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.EAT;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            Level level, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.canEat(true)) {
            player.startUsingItem(hand);
            return net.minecraft.world.InteractionResultHolder.consume(stack);
        }
        return net.minecraft.world.InteractionResultHolder.fail(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack remainder = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack armor = entity.getItemBySlot(slot);
                if (!armor.isEmpty()
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, armor) < 4) {
                    armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
                }
            }
        }
        return remainder;
    }
}
