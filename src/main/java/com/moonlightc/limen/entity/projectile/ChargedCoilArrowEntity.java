package com.moonlightc.limen.entity.projectile;

import com.moonlightc.limen.registry.ModEntities;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class ChargedCoilArrowEntity extends Arrow {
    public ChargedCoilArrowEntity(EntityType<? extends ChargedCoilArrowEntity> entityType, Level level) {
        super(entityType, level);
    }

    public ChargedCoilArrowEntity(Level level, LivingEntity shooter) {
        this(ModEntities.CHARGED_COIL_ARROW.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        this.pickup = AbstractArrow.Pickup.ALLOWED;
    }

    public ChargedCoilArrowEntity(Level level, double x, double y, double z) {
        this(ModEntities.CHARGED_COIL_ARROW.get(), level);
        this.setPos(x, y, z);
        this.pickup = AbstractArrow.Pickup.ALLOWED;
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(ModItems.CHARGED_COIL_ARROW.get());
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (!this.level().isClientSide) {
            this.level().addFreshEntity(new CoilFieldEntity(this.level(), hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z));
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (!this.level().isClientSide) {
            this.level().addFreshEntity(new CoilFieldEntity(this.level(), hitResult.getEntity()));
            this.discard();
        }
    }
}
