package com.moonlightc.limen.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Chainsword. Base stats match a vanilla diamond sword.
 *
 * <p>Enchanting is blocked at the enchanting table and the anvil ({@link #isEnchantable},
 * {@link #getEnchantmentValue}, {@link #isBookEnchantable}), but enchantments already present on
 * the stack still work normally — so the intended progression is to grant them through some other
 * mechanic (blueprint/upgrade/loot) rather than the vanilla enchanting flow.
 */
public class ChainswordItem extends SwordItem {
    public ChainswordItem(Properties properties) {
        this(Tiers.DIAMOND, properties);
    }

    public ChainswordItem(Tier tier, Properties properties) {
        super(tier, 3, -2.4F, properties);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    /** Blocks combining with enchanted books on an anvil. */
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    /** Blocks the anvil/table offering any specific enchantment for this item. */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }
}
