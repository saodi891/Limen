package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.item.ElectromagneticCrossbowItem;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class OverclockEffectEvents {
    private static final int OVERCLOCK_GLOWING_TICKS = 5 * 20;
    private static final float OVERCLOCK_GLOWING_DAMAGE_MULTIPLIER = 1.5F;

    private OverclockEffectEvents() {
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().hasEffect(ModMobEffects.OVERCLOCK.get())) {
            event.setCanceled(true);
        }
    }

    /**
     * Overclocked attackers amplify damage against already-glowing targets, then mark the target
     * with glowing themselves. The amplify check runs on the PRE-hit glowing state, so the first
     * hit only applies the mark and subsequent hits (while glowing lasts) get the 1.5x bonus.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !attacker.hasEffect(ModMobEffects.OVERCLOCK.get())) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (target.hasEffect(MobEffects.GLOWING)) {
            event.setAmount(event.getAmount() * OVERCLOCK_GLOWING_DAMAGE_MULTIPLIER);
        }

        if (!target.level().isClientSide) {
            // Non-stacking: fixed amplifier 0, re-applied to refresh duration only.
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, OVERCLOCK_GLOWING_TICKS, 0, false, true), attacker);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }

        boolean overclocked = player.hasEffect(ModMobEffects.OVERCLOCK.get());
        for (ItemStack crossbow : eligibleCrossbows(player)) {
            if (overclocked) {
                tickAutoReload(player, crossbow);
            } else {
                cancelAutoReload(crossbow);
            }
        }
    }

    /**
     * Crossbows that auto-reload: the off hand and any hotbar slot EXCEPT the selected (main-hand)
     * one. The main-hand crossbow is deliberately excluded so the player still loads it manually.
     * Ammo, by contrast, may come from anywhere in the inventory.
     */
    private static List<ItemStack> eligibleCrossbows(Player player) {
        List<ItemStack> result = new ArrayList<>();
        int selected = player.getInventory().selected;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof ElectromagneticCrossbowItem) {
                result.add(stack);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof ElectromagneticCrossbowItem) {
            result.add(offhand);
        }
        return result;
    }

    private static void tickAutoReload(Player player, ItemStack crossbow) {
        if (CrossbowItem.isCharged(crossbow)) {
            cancelAutoReload(crossbow);
            return;
        }

        ItemStack ammo = findArrowAmmo(player, crossbow);
        if (ammo.isEmpty()) {
            cancelAutoReload(crossbow);
            return;
        }

        long now = player.level().getGameTime();
        CompoundTag tag = crossbow.getOrCreateTag();
        if (!tag.contains(ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG)) {
            // Begin a reload cycle. Same charge duration as a manually-drawn crossbow (respects
            // Quick Charge), and the same loading-start sound, so it looks/sounds vanilla.
            tag.putLong(ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG, now);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CROSSBOW_LOADING_START, player.getSoundSource(), 0.8F, 1.0F);
            return;
        }

        int chargeDuration = CrossbowItem.getChargeDuration(crossbow);
        long elapsed = now - tag.getLong(ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG);
        if (elapsed < chargeDuration) {
            return;
        }

        loadProjectile(crossbow, ammo, player);
        tag.remove(ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG);
        ElectromagneticCrossbowItem.syncChargedModelData(crossbow);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_LOADING_END, player.getSoundSource(), 1.0F, 1.0F);
    }

    private static void cancelAutoReload(ItemStack crossbow) {
        CompoundTag tag = crossbow.getTag();
        if (tag != null && tag.contains(ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG)) {
            tag.remove(ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG);
        }
    }

    private static ItemStack findArrowAmmo(Player player, ItemStack crossbow) {
        if (player.getAbilities().instabuild) {
            return new ItemStack(Items.ARROW);
        }

        // Any slot in the main inventory (0-35) plus the off hand may supply ammo.
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isSupportedAmmo(crossbow, stack)) {
                return stack;
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (isSupportedAmmo(crossbow, offhand)) {
            return offhand;
        }

        return ItemStack.EMPTY;
    }

    private static boolean isSupportedAmmo(ItemStack crossbow, ItemStack candidate) {
        return !candidate.isEmpty()
                && candidate.getItem() instanceof ArrowItem
                && crossbow.getItem() instanceof CrossbowItem crossbowItem
                && crossbowItem.getAllSupportedProjectiles().test(candidate);
    }

    private static void loadProjectile(ItemStack crossbow, ItemStack ammo, Player player) {
        ItemStack loaded = ammo.copy();
        loaded.setCount(1);
        if (!player.getAbilities().instabuild) {
            ammo.shrink(1);
        }
        addChargedProjectile(crossbow, loaded);
        crossbow.getOrCreateTag().putBoolean("Charged", true);
    }

    private static void addChargedProjectile(ItemStack crossbow, ItemStack projectile) {
        CompoundTag tag = crossbow.getOrCreateTag();
        ListTag list = tag.contains("ChargedProjectiles", 9)
                ? tag.getList("ChargedProjectiles", 10)
                : new ListTag();
        CompoundTag projectileTag = new CompoundTag();
        projectile.save(projectileTag);
        list.add(projectileTag);
        tag.put("ChargedProjectiles", list);
    }
}
