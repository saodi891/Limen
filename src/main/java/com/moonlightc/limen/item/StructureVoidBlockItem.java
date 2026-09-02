package com.moonlightc.limen.item;

import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class StructureVoidBlockItem extends BlockItem implements Equipable {
    // Refresh a little before the effect elapses so it never flickers off, mirroring how the
    // turtle helmet keeps Water Breathing topped up while worn.
    private static final int EFFECT_DURATION_TICKS = 220;

    public StructureVoidBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    @Override
    public void onArmorTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player) {
        applyOverclock(player);
    }

    private void applyOverclock(LivingEntity wearer) {
        if (wearer.level().isClientSide) {
            return;
        }
        // Ambient (particle-light) infinite-feeling effect, refreshed each tick while worn.
        wearer.addEffect(new MobEffectInstance(ModMobEffects.OVERCLOCK.get(), EFFECT_DURATION_TICKS, 0, true, false, true));
    }
}
