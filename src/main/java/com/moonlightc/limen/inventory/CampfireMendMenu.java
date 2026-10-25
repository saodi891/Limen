package com.moonlightc.limen.inventory;

import com.mojang.datafixers.util.Pair;
import com.moonlightc.limen.registry.ModItems;
import com.moonlightc.limen.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * Sub-screen reached from the campfire hub's Mend option. Anvil-like: a damaged item in the left slot plus
 * industrial tape in the right slot yields a repaired copy in the result slot. The screen also surfaces the
 * player's armor and offhand slots so gear can be stripped and fed in without closing the hub. Each tape
 * restores {@link #REPAIR_FRACTION} of the item's max durability (rounded up); only the tapes actually
 * needed to reach full durability are spent when the result is taken.
 */
public class CampfireMendMenu extends AbstractContainerMenu {
    public static final int MEND_SLOT = 0;
    public static final int TAPE_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    public static final int BACK_BUTTON = 100;
    private static final int ARMOR_START = 3;
    private static final int OFFHAND_SLOT = 7;
    private static final int INV_START = 8;
    private static final int INV_END = 44;
    private static final int MAIN_INV_END = 35;

    /** Durability each industrial tape restores, as a fraction of the item's max durability. */
    private static final double REPAIR_FRACTION = 0.25D;

    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS
    };

    private final ContainerLevelAccess access;
    private final BlockPos campfirePos;
    private final Player menuPlayer;
    private final Container inputContainer;
    private final ResultContainer resultContainer = new ResultContainer();
    private int syncedSpent;
    private long lastSoundTime;

    /** The repaired preview plus the number of tapes it would cost. */
    private record MendResult(ItemStack output, int tapesUsed) {}

    /** Client-side constructor. The campfire position rides along in the open packet's buffer. */
    public CampfireMendMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, ContainerLevelAccess.NULL, data.readBlockPos());
    }

    public CampfireMendMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos campfirePos) {
        super(ModMenuTypes.CAMPFIRE_MEND.get(), containerId);
        this.access = access;
        this.campfirePos = campfirePos;
        this.menuPlayer = inventory.player;
        this.inputContainer = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                CampfireMendMenu.this.slotsChanged(this);
            }
        };

        this.addSlot(new Slot(this.inputContainer, MEND_SLOT, 62, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() && !isSpent();
            }
        });
        this.addSlot(new Slot(this.inputContainer, TAPE_SLOT, 98, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isTape(stack) && !isSpent();
            }
        });
        this.addSlot(new Slot(this.resultContainer, 0, 140, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                consumeMend();
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    CampfireHubMenu.grantCraftReward(serverPlayer);
                }
            }
        });

        for (int i = 0; i < 4; i++) {
            final EquipmentSlot equipment = ARMOR_SLOTS[i];
            final ResourceLocation icon = ARMOR_ICONS[i];
            this.addSlot(new Slot(inventory, 39 - i, 8, 18 + i * 18) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return equipment == Mob.getEquipmentSlotForItem(stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    ItemStack current = this.getItem();
                    return (current.isEmpty() || player.isCreative()
                            || !EnchantmentHelper.hasBindingCurse(current)) && super.mayPickup(player);
                }

                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
                }
            });
        }
        this.addSlot(new Slot(inventory, 40, 8, 96) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 138 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return menuPlayer instanceof net.minecraft.server.level.ServerPlayer sp
                        && CampfireHubMenu.hasSpentTonight(sp) ? 1 : 0;
            }

            @Override
            public void set(int value) {
                syncedSpent = value;
            }
        });
    }

    /** Whether this player has already used the campfire tonight — locks the input slots and the Back button. */
    private boolean isSpent() {
        if (this.menuPlayer instanceof net.minecraft.server.level.ServerPlayer sp) {
            return CampfireHubMenu.hasSpentTonight(sp);
        }
        return this.syncedSpent != 0;
    }

    public static boolean isTape(ItemStack stack) {
        return stack.is(ModItems.INDUSTRIAL_TAPE.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BACK_BUTTON && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
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

    private void createResult() {
        MendResult mend = computeMend(this.inputContainer.getItem(MEND_SLOT), this.inputContainer.getItem(TAPE_SLOT));
        this.resultContainer.setItem(0, mend == null ? ItemStack.EMPTY : mend.output());
        this.broadcastChanges();
    }

    /**
     * The repaired preview and the tapes it costs, or null when the inputs can't mend. Each tape restores
     * {@link #REPAIR_FRACTION} of the item's max durability (rounded up), and only as many tapes as are
     * needed to reach full durability are ever spent — leftover tape in the slot is untouched.
     */
    private static MendResult computeMend(ItemStack tool, ItemStack tape) {
        if (tool.getCount() != 1 || !tool.isDamageableItem() || !tool.isDamaged()
                || !isTape(tape) || tape.isEmpty()) {
            return null;
        }
        int perTape = Math.max(1, (int) Math.ceil(tool.getMaxDamage() * REPAIR_FRACTION));
        int damage = tool.getDamageValue();
        int tapesNeeded = (int) Math.ceil((double) damage / perTape);
        int tapesUsed = Math.min(tape.getCount(), tapesNeeded);
        int repaired = Math.min(damage, tapesUsed * perTape);
        ItemStack output = tool.copy();
        output.setDamageValue(damage - repaired);
        return new MendResult(output, tapesUsed);
    }

    /** One take == one repair: clears the tool slot and spends only the tapes the repair actually required. */
    private void consumeMend() {
        ItemStack tape = this.inputContainer.getItem(TAPE_SLOT);
        MendResult mend = computeMend(this.inputContainer.getItem(MEND_SLOT), tape);
        int used = mend == null ? 0 : mend.tapesUsed();
        this.inputContainer.setItem(MEND_SLOT, ItemStack.EMPTY);
        if (used > 0) {
            tape.shrink(used);
            this.inputContainer.setItem(TAPE_SLOT, tape.isEmpty() ? ItemStack.EMPTY : tape);
        }
        playMendSound();
    }

    private void playMendSound() {
        this.access.execute((level, pos) -> {
            long gameTime = level.getGameTime();
            if (this.lastSoundTime != gameTime) {
                level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.lastSoundTime = gameTime;
            }
        });
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
            if (!this.isNearLitCampfire()) {
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, original);
            return original;
        }

        if (index == MEND_SLOT || index == TAPE_SLOT || (index >= ARMOR_START && index <= OFFHAND_SLOT)) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (isTape(stack)) {
            if (!this.moveItemStackTo(stack, TAPE_SLOT, TAPE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.isDamageableItem()) {
            if (!this.moveItemStackTo(stack, MEND_SLOT, MEND_SLOT + 1, false)) {
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
