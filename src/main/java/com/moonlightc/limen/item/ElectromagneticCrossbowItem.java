package com.moonlightc.limen.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

public class ElectromagneticCrossbowItem extends CrossbowItem {
    public static final String FUTURE_COIL_ARROW_ID = "limen:charged_coil_arrow";
    public static final String SPECTRAL_ARROW_ID = "minecraft:spectral_arrow";
    public static final int MODEL_NONE = 0;
    public static final int MODEL_SPECTRAL = 1;
    public static final int MODEL_COIL = 2;
    /** 91 blocks per second at 20 game ticks per second, before crossbow spread. */
    public static final float ARROW_SPEED = 4.55F;

    // Gametime the current overclock auto-reload began; absent when not auto-reloading.
    public static final String AUTO_RELOAD_START_TAG = "LimenAutoReloadStart";

    public ElectromagneticCrossbowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CrossbowItem.isCharged(stack)) {
            return super.use(level, player, hand);
        }

        CrossbowItem.performShooting(level, player, hand, stack, ARROW_SPEED, 1.0F);
        CrossbowItem.setCharged(stack, false);
        syncChargedModelData(stack);
        return InteractionResultHolder.consume(stack);
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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        syncChargedModelData(stack);
    }

    public static void syncChargedModelData(ItemStack stack) {
        int model = MODEL_NONE;
        if (isChargedWithSpectralArrow(stack)) {
            model = MODEL_SPECTRAL;
        } else if (isChargedWithFutureCoilArrow(stack)) {
            model = MODEL_COIL;
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (model == MODEL_NONE) {
            tag.remove("CustomModelData");
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
            return;
        }

        tag.putInt("CustomModelData", model);
    }

    public static boolean isChargedWithFutureCoilArrow(ItemStack stack) {
        return hasChargedProjectile(stack, FUTURE_COIL_ARROW_ID);
    }

    public static boolean isChargedWithSpectralArrow(ItemStack stack) {
        return hasChargedProjectile(stack, SPECTRAL_ARROW_ID);
    }

    public static boolean hasChargedProjectile(ItemStack stack, Item item) {
        return hasChargedProjectile(stack, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private static boolean hasChargedProjectile(ItemStack stack, String itemId) {
        if (!CrossbowItem.isCharged(stack)) {
            return false;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("ChargedProjectiles", 9)) {
            return false;
        }

        ListTag chargedProjectiles = tag.getList("ChargedProjectiles", 10);
        for (int i = 0; i < chargedProjectiles.size(); i++) {
            CompoundTag projectileTag = chargedProjectiles.getCompound(i);
            if (itemId.equals(projectileTag.getString("id"))) {
                return true;
            }
        }

        return false;
    }
}
