package com.moonlightc.limen.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraft.world.entity.EntityType;

public final class PamaSpawnEggItem extends ForgeSpawnEggItem {
    public PamaSpawnEggItem(
            java.util.function.Supplier<? extends EntityType<? extends Mob>> entityType,
            int backgroundColor,
            int highlightColor,
            Properties properties
    ) {
        super(entityType, backgroundColor, highlightColor, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = serverLevel.getBlockState(clickedPos);
        BlockEntity blockEntity = serverLevel.getBlockEntity(clickedPos);
        if (clickedState.is(Blocks.SPAWNER) && blockEntity instanceof SpawnerBlockEntity) {
            return super.useOn(context);
        }

        BlockPos spawnPos = clickedState.getCollisionShape(serverLevel, clickedPos).isEmpty()
                ? clickedPos
                : clickedPos.above();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        EntityType<?> type = getType(stack.getTag());

        Mob spawned = (Mob) type.spawn(
                serverLevel,
                stack,
                player,
                spawnPos,
                MobSpawnType.SPAWN_EGG,
                true,
                clickedPos.equals(spawnPos) && context.getClickedFace() == Direction.UP
        );
        if (spawned != null) {
            spawned.setPos(spawned.getX() + 0.5D, spawned.getY(), spawned.getZ());
            stack.shrink(1);
            serverLevel.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, clickedPos);
        }
        return InteractionResult.CONSUME;
    }
}
