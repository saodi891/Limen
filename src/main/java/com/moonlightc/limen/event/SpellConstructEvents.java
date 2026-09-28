package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.api.tag.LimenEntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class SpellConstructEvents {
    private static final float ENCHANTED_DAMAGE_MULTIPLIER = 1.2F;
    private static final float UNENCHANTED_DAMAGE_MULTIPLIER = 0.8F;

    private SpellConstructEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !target.getType().is(LimenEntityTypeTags.SPELL_CONSTRUCTS)) {
            return;
        }

        ItemStack attackItem = findAttackItem(event.getSource().getDirectEntity(), event.getSource().getEntity());
        float multiplier = isEnchanted(attackItem)
                ? ENCHANTED_DAMAGE_MULTIPLIER
                : UNENCHANTED_DAMAGE_MULTIPLIER;
        event.setAmount(event.getAmount() * multiplier);
    }

    private static ItemStack findAttackItem(Entity directEntity, Entity sourceEntity) {
        if (directEntity instanceof Projectile && sourceEntity instanceof LivingEntity owner) {
            ItemStack mainHand = owner.getMainHandItem();
            if (isRangedWeapon(mainHand)) {
                return mainHand;
            }
            ItemStack offHand = owner.getOffhandItem();
            return isRangedWeapon(offHand) ? offHand : ItemStack.EMPTY;
        }

        if (directEntity instanceof LivingEntity attacker) {
            ItemStack useItem = attacker.getUseItem();
            return useItem.isEmpty() ? attacker.getMainHandItem() : useItem;
        }

        return ItemStack.EMPTY;
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    private static boolean isEnchanted(ItemStack stack) {
        return !stack.isEmpty() && !EnchantmentHelper.getEnchantments(stack).isEmpty();
    }
}
