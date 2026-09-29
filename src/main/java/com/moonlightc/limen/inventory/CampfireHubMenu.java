package com.moonlightc.limen.inventory;

import com.moonlightc.limen.event.CampfireRestManager;
import com.moonlightc.limen.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * Options hub opened by holding a marshmallow over a lit campfire at night. Replaces the roasting
 * station: it owns no slots, only a 3x2 button grid handled server-side through {@link #clickMenuButton}.
 *
 * <p>Only Rest and Exit are wired this iteration; the remaining four buttons are accepted but do
 * nothing yet. Pressing any button other than Exit registers the player as resting: the night only
 * fast-forwards once enough players rest or sleep to meet the {@code playersSleepingPercentage}
 * threshold (see {@link com.moonlightc.limen.event.CampfireRestManager}), and resting deliberately does
 * <em>not</em> set the player's respawn point. Rest additionally grants Regeneration II for five seconds
 * and roasts the held marshmallow into its cooked counterpart.
 */
public class CampfireHubMenu extends AbstractContainerMenu implements CampfireScene {
    public static final int BUTTON_REST = 0;
    public static final int BUTTON_EXIT = 1;
    public static final int BUTTON_WEAVE = 2;
    public static final int BUTTON_MEND = 3;
    public static final int BUTTON_ENCHANT = 4;
    public static final int BUTTON_BREW = 5;
    public static final int BUTTON_COUNT = 6;

    /** Regeneration II for five seconds — the reward for resting by a campfire. */
    private static final int REGEN_TICKS = 100;
    private static final int REGEN_AMPLIFIER = 1;

    private final ContainerLevelAccess access;
    private final BlockPos campfirePos;

    /** Client-side constructor. The campfire position rides along in the open packet's buffer. */
    public CampfireHubMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, ContainerLevelAccess.NULL, data.readBlockPos());
    }

    public CampfireHubMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos campfirePos) {
        super(ModMenuTypes.CAMPFIRE_HUB.get(), containerId);
        this.access = access;
        this.campfirePos = campfirePos;
    }

    @Override
    public BlockPos getCampfirePos() {
        return this.campfirePos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= BUTTON_COUNT) {
            return false;
        }
        if (id == BUTTON_EXIT) {
            player.closeContainer();
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            // Rest is the one wired reward: a short Regeneration burst and roasting the marshmallow the
            // player opened the hub with. The other buttons are still placeholders.
            if (id == BUTTON_REST) {
                restByCampfire(serverPlayer);
            }
            // Any non-exit button counts the player as resting — a vote to skip the night, like climbing into
            // a bed. The night only fast-forwards once enough players rest or sleep (see CampfireRestManager),
            // so campfire rest and bed sleep stack toward the same playersSleepingPercentage threshold.
            CampfireRestManager.addResting(serverPlayer);
        }
        player.closeContainer();
        return true;
    }

    /** Grants Regeneration II for five seconds and roasts the held marshmallow into its cooked counterpart. */
    private void restByCampfire(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, REGEN_AMPLIFIER));
        for (InteractionHand hand : InteractionHand.values()) {
            Item roasted = CampfireMenu.roastResult(player.getItemInHand(hand));
            if (roasted != null) {
                player.setItemInHand(hand, new ItemStack(roasted));
                break;
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) -> CampfireBlock.isLitCampfire(level.getBlockState(pos))
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
