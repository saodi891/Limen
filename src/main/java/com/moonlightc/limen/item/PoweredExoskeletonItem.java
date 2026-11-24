package com.moonlightc.limen.item;

import com.moonlightc.limen.entity.client.PoweredExoskeletonArmorRenderer;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;
import java.util.function.Consumer;

public class PoweredExoskeletonItem extends ArmorItem implements GeoItem {
    private static final UUID MOVEMENT_SPEED_MODIFIER_ID = UUID.fromString("8f57c34b-aef7-4634-873f-1c4de9a1538b");
    private static final double WALK_SPEED_BONUS = 0.016214;
    private static final double SPRINT_SPEED_BONUS = 0.012473;
    private static final AttributeModifier WALK_SPEED_MODIFIER = new AttributeModifier(
            MOVEMENT_SPEED_MODIFIER_ID, "Powered exoskeleton movement", WALK_SPEED_BONUS, AttributeModifier.Operation.ADDITION);
    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 40;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return type == ArmorItem.Type.LEGGINGS ? 6 : 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "limen:powered_exoskeleton";
        }

        @Override
        public float getToughness() {
            return 2.5F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public PoweredExoskeletonItem(Item.Properties properties) {
        super(MATERIAL, ArmorItem.Type.LEGGINGS, properties);
    }

    public static UUID movementSpeedModifierId() {
        return MOVEMENT_SPEED_MODIFIER_ID;
    }

    public static double movementSpeedBonus(boolean sprinting) {
        return sprinting ? SPRINT_SPEED_BONUS : WALK_SPEED_BONUS;
    }

    public static AttributeModifier movementSpeedModifier(boolean sprinting) {
        return new AttributeModifier(MOVEMENT_SPEED_MODIFIER_ID, "Powered exoskeleton movement",
                movementSpeedBonus(sprinting), AttributeModifier.Operation.ADDITION);
    }

    public static boolean isEquippedBy(Player player) {
        ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
        return leggings.is(ModItems.POWERED_EXOSKELETON.get())
                && !isWornOut(leggings);
    }

    public static boolean isWornOut(ItemStack stack) {
        return stack.is(ModItems.POWERED_EXOSKELETON.get())
                && stack.getMaxDamage() > 0
                && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    public static boolean shouldIgnoreUseSlowdown(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof Player player) || !isEquippedBy(player) || stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        return item.isEdible()
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof ShieldItem
                || item instanceof TridentItem
                || item instanceof PotionItem
                || stack.is(Items.GOAT_HORN);
    }

    public static void updateMovementModifier(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        if (!isEquippedBy(player)) {
            speed.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);
            return;
        }

        double desired = movementSpeedBonus(player.isSprinting());
        AttributeModifier current = speed.getModifier(MOVEMENT_SPEED_MODIFIER_ID);
        if (current != null && Math.abs(current.getAmount() - desired) < 1.0E-7D) {
            return;
        }

        speed.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);
        speed.addTransientModifier(movementSpeedModifier(player.isSprinting()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private PoweredExoskeletonArmorRenderer armorRenderer;

            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                    net.minecraft.client.model.HumanoidModel<?> original) {
                if (this.armorRenderer == null) {
                    this.armorRenderer = new PoweredExoskeletonArmorRenderer();
                }
                this.armorRenderer.prepForRender(entity, stack, slot, original);
                return this.armorRenderer;
            }
        });
    }

    @Override
    public com.google.common.collect.Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        com.google.common.collect.Multimap<Attribute, AttributeModifier> defaults = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.LEGS) {
            return defaults;
        }
        com.google.common.collect.ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = com.google.common.collect.ImmutableMultimap.builder();
        builder.putAll(defaults);
        builder.put(Attributes.MOVEMENT_SPEED, WALK_SPEED_MODIFIER);
        return builder.build();
    }
}
