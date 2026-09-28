package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.inventory.CampfireMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * Opens the roasting station when a player right-clicks a lit campfire while holding a marshmallow
 * in the main hand, and drives its progress.
 *
 * <p>The interaction has to be intercepted here rather than in a block override: the campfire is a
 * vanilla block, and vanilla {@code CampfireBlock.use} would claim the click first for any held stack
 * that has a {@code campfire_cooking} recipe. {@code RightClickBlock} fires ahead of {@code use}.
 *
 * <p>Progress is ticked from the player rather than the block entity because the station's contents
 * live on the menu, so roasting is only meaningful while the screen is open.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class CampfireRoastEvents {
    private CampfireRoastEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (CampfireMenu.roastResult(held) == null) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!CampfireBlock.isLitCampfire(state)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide()));

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, level, pos, state);
        }
    }

    private static void open(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
        Component title = Component.translatable(state.is(Blocks.SOUL_CAMPFIRE)
                ? "container.limen.soul_campfire"
                : "container.limen.campfire");
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);

        NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CampfireMenu(containerId, inventory,
                        new SimpleContainer(CampfireMenu.ROAST_SLOTS),
                        new SimpleContainerData(CampfireMenu.ROAST_SLOTS),
                        access),
                title), buffer -> buffer.writeBlockPos(pos));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        if (player.containerMenu instanceof CampfireMenu menu) {
            menu.roastTick();
        }
    }
}
