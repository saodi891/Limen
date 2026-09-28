package com.moonlightc.limen.mixin;

import com.moonlightc.limen.registry.ModEnchantments;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    private static final int EXTRA_DURABILITY_MULTIPLIER = 100;
    private static final float TRIGGER_CHANCE = 0.25F;

    @ModifyArg(
            method = "hurtAndBreak",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z"
            ),
            index = 0
    )
    private int limen$applyErosionCurse(int amount) {
        ItemStack stack = (ItemStack) (Object) this;
        if (amount <= 0
                || stack.getItem() instanceof ArmorItem
                || EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.EROSION_CURSE.get(), stack) <= 0) {
            return amount;
        }

        RandomSource random = RandomSource.create();
        if (random.nextFloat() < TRIGGER_CHANCE) {
            long multipliedAmount = (long) amount * (EXTRA_DURABILITY_MULTIPLIER + 1);
            int remainingBeforeBreak = stack.getMaxDamage() - stack.getDamageValue() - 1;
            return Math.max(0, Math.min(remainingBeforeBreak, (int) Math.min(Integer.MAX_VALUE, multipliedAmount)));
        }
        return amount;
    }
}
