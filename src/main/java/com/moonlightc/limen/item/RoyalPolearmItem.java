package com.moonlightc.limen.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.moonlightc.limen.api.LimenPlayerSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class RoyalPolearmItem extends SwordItem {
    public static final int SPECIAL_USE_DURATION = 72000;
    private static final UUID ENTITY_REACH_MODIFIER_UUID = UUID.fromString("e8cf7e42-0a97-4a42-b3f0-5b2cf66f65dd");
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public RoyalPolearmItem(Properties properties) {
        this(Tiers.DIAMOND, properties);
    }

    public RoyalPolearmItem(Tier tier, Properties properties) {
        super(tier, 6, -3.05F, properties);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(super.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND));
        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                ENTITY_REACH_MODIFIER_UUID,
                "Royal polearm reach",
                1.5D,
                AttributeModifier.Operation.ADDITION
        ));
        this.defaultModifiers = builder.build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!LimenPlayerSkills.hasPolearmCombatTechniques(player)) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(usedHand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, player.getSoundSource(), 0.35F, 0.85F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return SPECIAL_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // Not SPEAR: that forces vanilla's overhead trident-throw pose, which clobbers the custom
        // charge pose in ClientPolearmPose. NONE keeps the arm at ArmPose.ITEM so the pose takes over.
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("tooltip.limen.royal_polearm.0").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.limen.royal_polearm.1").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot equipmentSlot) {
        return equipmentSlot == EquipmentSlot.MAINHAND ? this.defaultModifiers : super.getDefaultAttributeModifiers(equipmentSlot);
    }
}
