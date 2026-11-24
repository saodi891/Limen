package com.moonlightc.limen.item;

import com.moonlightc.limen.entity.MechanicTrappedState;
import com.moonlightc.limen.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeSpawnEggItem;

public final class MechanicSpawnEggItem extends ForgeSpawnEggItem {
    public MechanicSpawnEggItem(Properties properties) {
        super(() -> net.minecraft.world.entity.EntityType.VILLAGER, 0xA88D72, 0x4B5961, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = serverLevel.getBlockState(clickedPos);
        BlockPos spawnPos = clickedState.getCollisionShape(serverLevel, clickedPos).isEmpty()
                ? clickedPos
                : clickedPos.above();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        Villager villager = net.minecraft.world.entity.EntityType.VILLAGER.spawn(
                serverLevel,
                stack,
                player,
                spawnPos,
                MobSpawnType.SPAWN_EGG,
                true,
                clickedPos.equals(spawnPos) && context.getClickedFace() == Direction.UP
        );
        if (villager != null) {
            villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.MECHANIC.get()));
            MechanicTrappedState.initialize(villager, true);
            MechanicTrappedState.sync(villager);
            stack.shrink(1);
            serverLevel.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, clickedPos);
        }
        return InteractionResult.CONSUME;
    }
}
