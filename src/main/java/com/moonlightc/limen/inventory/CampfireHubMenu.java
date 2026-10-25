package com.moonlightc.limen.inventory;

import com.moonlightc.limen.event.CampfireRestManager;
import com.moonlightc.limen.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

    /**
     * Persistent flag recording the day-number on which the player last obtained a campfire craft (or rested).
     * Stored in the Forge-persisted sub-tag so it survives relog/death. One campfire use per night: a night is
     * the tail of a single day-number window, so comparing against the current day gates the whole night.
     */
    private static final String SPENT_DAY_KEY = "limen:campfire_spent_day";
    private static final long DAY_LENGTH = 24000L;

    private final ContainerLevelAccess access;
    private final BlockPos campfirePos;
    private final boolean enchantUnlocked;
    private final boolean brewUnlocked;

    /** Client-side constructor. The campfire position rides along in the open packet's buffer. */
    public CampfireHubMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, ContainerLevelAccess.NULL, data.readBlockPos(), data.readBoolean(),
                data.readBoolean());
    }

    public CampfireHubMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos campfirePos,
                           boolean enchantUnlocked, boolean brewUnlocked) {
        super(ModMenuTypes.CAMPFIRE_HUB.get(), containerId);
        this.access = access;
        this.campfirePos = campfirePos;
        this.enchantUnlocked = enchantUnlocked;
        this.brewUnlocked = brewUnlocked;
    }

    @Override
    public BlockPos getCampfirePos() {
        return this.campfirePos;
    }

    /** Whether the player has the vanilla Enchanter advancement — gates the hub's Enchant option. */
    public boolean isEnchantUnlocked() {
        return this.enchantUnlocked;
    }

    /** Whether the player has the vanilla Local Brewery advancement — gates the hub's Brew option. */
    public boolean isBrewUnlocked() {
        return this.brewUnlocked;
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
        if (id == BUTTON_ENCHANT) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (CampfireEnchantMenu.hasEnchanterAdvancement(serverPlayer)) {
                    openEnchantMenu(serverPlayer);
                } else {
                    serverPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.limen.enchant_locked"), true);
                    serverPlayer.closeContainer();
                }
            }
            return true;
        }
        if (id == BUTTON_BREW) {
            if (player instanceof ServerPlayer serverPlayer) {
                if (CampfireBrewMenu.hasBreweryAdvancement(serverPlayer)) {
                    openBrewMenu(serverPlayer);
                } else {
                    serverPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.limen.brew_locked"), true);
                    serverPlayer.closeContainer();
                }
            }
            return true;
        }
        if (id == BUTTON_MEND) {
            if (player instanceof ServerPlayer serverPlayer) {
                openMendMenu(serverPlayer);
            }
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (id == BUTTON_REST) {
                // Rest is the one wired reward: the regen burst, a roasted marshmallow and a vote to skip the
                // night, all bundled into grantRestReward.
                grantRestReward(serverPlayer);
            } else {
                // Any other non-exit button (the WEAVE placeholder) still counts the player as resting — a vote
                // to skip the night, like climbing into a bed — but earns no reward yet. The night only
                // fast-forwards once enough players rest or sleep (see CampfireRestManager), so campfire rest and
                // bed sleep stack toward the same playersSleepingPercentage threshold.
                CampfireRestManager.addResting(serverPlayer);
            }
        }
        player.closeContainer();
        return true;
    }

    /**
     * The full campfire-rest reward for pressing Rest: Regeneration II for five seconds on top of the shared
     * craft reward (roasted marshmallow + a vote to skip the night).
     */
    public static void grantRestReward(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, REGEN_AMPLIFIER));
        grantCraftReward(player);
    }

    /**
     * The craft-completion reward shared by the mend, enchant and brew sub-screens: the held marshmallow roasted
     * into its cooked counterpart and the player counted as resting (a vote to skip the night). Deliberately omits
     * Regeneration — that is reserved for players who actually choose Rest. Also marks the player as having used
     * the campfire tonight ({@link #markSpentTonight}): one craft per night, after which the sub-menus lock.
     */
    public static void grantCraftReward(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            Item roasted = CampfireMenu.roastResult(player.getItemInHand(hand));
            if (roasted != null) {
                player.setItemInHand(hand, new ItemStack(roasted));
                break;
            }
        }
        CampfireRestManager.addResting(player);
        markSpentTonight(player);
    }

    /** Records that the player has used the campfire tonight (one craft/rest per night). */
    public static void markSpentTonight(ServerPlayer player) {
        persistentData(player).putLong(SPENT_DAY_KEY, currentDay(player));
    }

    /** True once the player has obtained a craft (or rested) during the current night's day-window. */
    public static boolean hasSpentTonight(ServerPlayer player) {
        CompoundTag data = persistentData(player);
        return data.contains(SPENT_DAY_KEY) && data.getLong(SPENT_DAY_KEY) == currentDay(player);
    }

    private static long currentDay(ServerPlayer player) {
        return Math.floorDiv(player.level().getDayTime(), DAY_LENGTH);
    }

    private static CompoundTag persistentData(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag data = root.getCompound(Player.PERSISTED_NBT_TAG);
        root.put(Player.PERSISTED_NBT_TAG, data);
        return data;
    }

    /** Opens the enchant sub-screen tied to the same campfire, closing this hub in the process. */
    private void openEnchantMenu(ServerPlayer player) {
        BlockPos pos = this.campfirePos;
        ContainerLevelAccess menuAccess = this.access;
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CampfireEnchantMenu(containerId, inventory, menuAccess, pos),
                net.minecraft.network.chat.Component.translatable("container.limen.campfire_enchant")),
                buffer -> buffer.writeBlockPos(pos));
    }

    /** Opens the brew sub-screen tied to the same campfire, closing this hub in the process. */
    private void openBrewMenu(ServerPlayer player) {
        BlockPos pos = this.campfirePos;
        ContainerLevelAccess menuAccess = this.access;
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CampfireBrewMenu(containerId, inventory, menuAccess, pos),
                net.minecraft.network.chat.Component.translatable("container.limen.campfire_brew")),
                buffer -> buffer.writeBlockPos(pos));
    }

    /** Opens the mend sub-screen tied to the same campfire, closing this hub in the process. */
    private void openMendMenu(ServerPlayer player) {
        BlockPos pos = this.campfirePos;
        ContainerLevelAccess menuAccess = this.access;
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CampfireMendMenu(containerId, inventory, menuAccess, pos),
                net.minecraft.network.chat.Component.translatable("container.limen.campfire_mend")),
                buffer -> buffer.writeBlockPos(pos));
    }

    /**
     * Reopens the options hub for this campfire, recomputing the enchant/brew advancement gates. Used by the
     * sub-screens' Back button so the player can bail out of a craft and pick a different option.
     */
    public static void openHub(ServerPlayer player, ContainerLevelAccess access, BlockPos pos) {
        boolean enchantUnlocked = CampfireEnchantMenu.hasEnchanterAdvancement(player);
        boolean brewUnlocked = CampfireBrewMenu.hasBreweryAdvancement(player);
        net.minecraft.network.chat.Component title = access.evaluate((level, ignored) ->
                level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                        ? net.minecraft.network.chat.Component.translatable("container.limen.soul_campfire")
                        : net.minecraft.network.chat.Component.translatable("container.limen.campfire"),
                net.minecraft.network.chat.Component.translatable("container.limen.campfire"));
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CampfireHubMenu(containerId, inventory, access, pos,
                        enchantUnlocked, brewUnlocked),
                title), buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeBoolean(enchantUnlocked);
                    buffer.writeBoolean(brewUnlocked);
                });
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
