package com.moonlightc.limen.inventory;

import com.moonlightc.limen.registry.ModMenuTypes;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * Sub-screen reached from the campfire hub's Brew option. An anvil-like layout: the left slot takes a
 * potion, the right slot takes plain arrows, and the result slot yields that many tipped arrows carrying
 * the potion's effects. Taking the result spends one potion and every arrow in the right slot. This is the
 * "tier 1" behaviour; richer results come later.
 */
public class CampfireBrewMenu extends AbstractContainerMenu {
    public static final int POTION_SLOT = 0;
    public static final int ARROW_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    public static final int BACK_BUTTON = 100;
    private static final int ARROW_MAX = 16;
    private static final int INV_START = 3;
    private static final int INV_END = 39;
    private static final int MAIN_INV_END = 30;

    private static final ResourceLocation BREWERY_ADVANCEMENT =
            new ResourceLocation("minecraft", "nether/brew_potion");

    private final ContainerLevelAccess access;
    private final BlockPos campfirePos;
    private final Player menuPlayer;
    private final Container inputContainer;
    private final ResultContainer resultContainer = new ResultContainer();
    private int syncedSpent;
    private long lastSoundTime;

    /** Client-side constructor. The campfire position rides along in the open packet's buffer. */
    public CampfireBrewMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, ContainerLevelAccess.NULL, data.readBlockPos());
    }

    public CampfireBrewMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos campfirePos) {
        super(ModMenuTypes.CAMPFIRE_BREW.get(), containerId);
        this.access = access;
        this.campfirePos = campfirePos;
        this.menuPlayer = inventory.player;
        this.inputContainer = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                CampfireBrewMenu.this.slotsChanged(this);
            }
        };

        this.addSlot(new Slot(this.inputContainer, POTION_SLOT, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isPotion(stack) && !isSpent();
            }
        });
        this.addSlot(new Slot(this.inputContainer, ARROW_SLOT, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isArrow(stack) && !isSpent();
            }

            @Override
            public int getMaxStackSize() {
                return ARROW_MAX;
            }
        });
        this.addSlot(new Slot(this.resultContainer, 0, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                consumeBatch();
                if (player instanceof ServerPlayer serverPlayer) {
                    CampfireHubMenu.grantCraftReward(serverPlayer);
                }
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 118 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 176));
        }

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return menuPlayer instanceof ServerPlayer sp && CampfireHubMenu.hasSpentTonight(sp) ? 1 : 0;
            }

            @Override
            public void set(int value) {
                syncedSpent = value;
            }
        });
    }

    /** Whether this player has already used the campfire tonight — locks the input slots and the Back button. */
    private boolean isSpent() {
        if (this.menuPlayer instanceof ServerPlayer sp) {
            return CampfireHubMenu.hasSpentTonight(sp);
        }
        return this.syncedSpent != 0;
    }

    public static boolean isPotion(ItemStack stack) {
        return stack.getItem() instanceof PotionItem;
    }

    public static boolean isArrow(ItemStack stack) {
        return stack.is(Items.ARROW);
    }

    public static boolean hasBreweryAdvancement(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        Advancement advancement = server.getAdvancements().getAdvancement(BREWERY_ADVANCEMENT);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BACK_BUTTON && player instanceof ServerPlayer serverPlayer) {
            if (CampfireHubMenu.hasSpentTonight(serverPlayer)) {
                serverPlayer.closeContainer();
            } else {
                CampfireHubMenu.openHub(serverPlayer, this.access, this.campfirePos);
            }
            return true;
        }
        return false;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.inputContainer) {
            createResult();
        }
    }

    /**
     * Rebuilds the result from the current inputs: up to 16 arrows + 1 potion -> that many tipped arrows of
     * that potion. Every potion type is accepted in the left slot, but a result only appears when the potion
     * actually carries at least one effect (an "arrow of ..." exists for it) — plain water, awkward, mundane
     * and thick potions have no target tipped arrow, so they brew nothing.
     */
    private void createResult() {
        ItemStack potion = this.inputContainer.getItem(POTION_SLOT);
        ItemStack arrows = this.inputContainer.getItem(ARROW_SLOT);
        if (isPotion(potion) && isArrow(arrows) && !arrows.isEmpty()
                && !PotionUtils.getMobEffects(potion).isEmpty()) {
            ItemStack result = new ItemStack(Items.TIPPED_ARROW, arrows.getCount());
            PotionUtils.setPotion(result, PotionUtils.getPotion(potion));
            PotionUtils.setCustomEffects(result, PotionUtils.getCustomEffects(potion));
            this.resultContainer.setItem(0, result);
        } else {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
        }
        this.broadcastChanges();
    }

    /** One take == one batch: a single potion plus every arrow currently in the right slot. */
    private void consumeBatch() {
        ItemStack potion = this.inputContainer.getItem(POTION_SLOT);
        if (!potion.isEmpty()) {
            potion.shrink(1);
            this.inputContainer.setItem(POTION_SLOT, potion.isEmpty() ? ItemStack.EMPTY : potion);
        }
        this.inputContainer.setItem(ARROW_SLOT, ItemStack.EMPTY);
        playBrewSound();
    }

    private void playBrewSound() {
        this.access.execute((level, pos) -> {
            long gameTime = level.getGameTime();
            if (this.lastSoundTime != gameTime) {
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.lastSoundTime = gameTime;
            }
        });
    }

    private boolean hasRoomFor(ItemStack stack) {
        int needed = stack.getCount();
        for (int i = INV_START; i < INV_END && needed > 0; i++) {
            ItemStack slotStack = this.slots.get(i).getItem();
            if (slotStack.isEmpty()) {
                needed -= stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(slotStack, stack)) {
                needed -= slotStack.getMaxStackSize() - slotStack.getCount();
            }
        }
        return needed <= 0;
    }

    private boolean isNearLitCampfire() {
        return this.access.evaluate(
                (level, pos) -> CampfireBlock.isLitCampfire(level.getBlockState(this.campfirePos)), true);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) ->
                CampfireBlock.isLitCampfire(level.getBlockState(this.campfirePos))
                        && player.distanceToSqr(this.campfirePos.getX() + 0.5D, this.campfirePos.getY() + 0.5D,
                        this.campfirePos.getZ() + 0.5D) <= 64.0D, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == RESULT_SLOT) {
            if (!this.isNearLitCampfire() || !hasRoomFor(stack)) {
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, original);
            return original;
        }

        if (index == POTION_SLOT || index == ARROW_SLOT) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (isPotion(stack)) {
            if (!this.moveItemStackTo(stack, POTION_SLOT, POTION_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isArrow(stack)) {
            if (!this.moveItemStackTo(stack, ARROW_SLOT, ARROW_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < MAIN_INV_END) {
            if (!this.moveItemStackTo(stack, MAIN_INV_END, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, MAIN_INV_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.inputContainer);
    }
}
