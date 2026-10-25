package com.moonlightc.limen.inventory;

import com.moonlightc.limen.registry.ModItems;
import com.moonlightc.limen.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.CampfireBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Sub-screen reached from the campfire hub's Enchant option. Holds a single input slot for an
 * unenchanted weapon plus the player inventory. The offer list is a deterministic function of the
 * input item type, the player's enchanter tier (0/1/2) and a persistent per-player seed, so both
 * sides compute the same offers without an extra packet. Each tier offers 3/4/5 enchantments at
 * levels 1-2 / 3-4 / 5-6 (clamped to each enchantment's max). Applying one spends that offer's
 * variable point cost; rerolling spends one point and advances the seed.
 */
public class CampfireEnchantMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int REROLL_BUTTON = 10;
    public static final int BACK_BUTTON = 100;
    private static final int INVENTORY_START = 1;
    private static final int INVENTORY_END = 37;
    private static final int MAIN_INVENTORY_END = 28;

    private static final int MAX_TIER = 2;
    private static final int REROLL_COST = 1;
    private static final String TIER_KEY = "limen:enchant_tier";
    private static final String SEED_KEY = "limen:enchant_seed";
    private static final ResourceLocation ENCHANTER_ADVANCEMENT =
            new ResourceLocation("minecraft", "story/enchant_item");

    private final ContainerLevelAccess access;
    private final BlockPos campfirePos;
    private final Inventory playerInventory;
    private final Container inputContainer;
    private final Slot inputSlot;
    private final DataSlot tierData = DataSlot.standalone();
    private final DataSlot seedData = DataSlot.standalone();
    private int syncedSpent;
    private long lastSoundTime;

    /** A single rolled enchantment option: the enchantment, its clamped level and its point cost. */
    public record EnchantOffer(Enchantment enchantment, int level, int cost) {}

    /** Client-side constructor. The campfire position rides along in the open packet's buffer. */
    public CampfireEnchantMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, ContainerLevelAccess.NULL, data.readBlockPos());
    }

    public CampfireEnchantMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos campfirePos) {
        super(ModMenuTypes.CAMPFIRE_ENCHANT.get(), containerId);
        this.access = access;
        this.campfirePos = campfirePos;
        this.playerInventory = inventory;
        this.inputContainer = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                CampfireEnchantMenu.this.broadcastChanges();
            }
        };

        this.inputSlot = this.addSlot(new Slot(this.inputContainer, INPUT_SLOT, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !availableEnchantments(stack).isEmpty() && !isSpent();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 174 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 232));
        }

        this.addDataSlot(this.tierData);
        this.addDataSlot(this.seedData);
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            CompoundTag persisted = persistentData(serverPlayer);
            this.tierData.set(Math.max(0, Math.min(MAX_TIER, persisted.getInt(TIER_KEY))));
            int seed;
            if (persisted.contains(SEED_KEY)) {
                seed = persisted.getInt(SEED_KEY);
            } else {
                // Keep the seed inside signed-short range: container DataSlots sync as 16-bit shorts, so a
                // wider value would truncate on the client and desync the offers it computes from ours.
                seed = serverPlayer.getRandom().nextInt(65536) - 32768;
                persisted.putInt(SEED_KEY, seed);
            }
            this.seedData.set(seed);
        }

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return inventory.player instanceof ServerPlayer sp && CampfireHubMenu.hasSpentTonight(sp) ? 1 : 0;
            }

            @Override
            public void set(int value) {
                syncedSpent = value;
            }
        });
    }

    /** Whether this player has already used the campfire tonight — locks the input slot, apply and Back. */
    private boolean isSpent() {
        if (this.playerInventory.player instanceof ServerPlayer sp) {
            return CampfireHubMenu.hasSpentTonight(sp);
        }
        return this.syncedSpent != 0;
    }

    public ItemStack getInput() {
        return this.inputSlot.getItem();
    }

    public int getTier() {
        return this.tierData.get();
    }

    public int getSeed() {
        return this.seedData.get();
    }

    /** Counts the enchantment points the player is carrying — drives both the server spend and the client UI. */
    public int getEnchantmentPoints() {
        int total = 0;
        for (int i = 0; i < this.playerInventory.getContainerSize(); i++) {
            ItemStack stack = this.playerInventory.getItem(i);
            if (stack.is(ModItems.ENCHANTMENT_POINTS.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }
    /**
     * Vanilla, non-curse, non-treasure enchantments the stack can accept — but only while it carries no
     * enchantments yet. Returns an empty list for anything already enchanted or non-enchantable, which is
     * also what gates the input slot. Deterministic, so client and server derive the same candidate pool.
     */
    public static List<Enchantment> availableEnchantments(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1 || stack.isEnchanted()) {
            return List.of();
        }
        List<Enchantment> result = new ArrayList<>();
        for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
            if (enchantment.isCurse() || enchantment.isTreasureOnly()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (key == null || !key.getNamespace().equals("minecraft")) {
                continue;
            }
            if (!enchantment.canEnchant(stack)) {
                continue;
            }
            result.add(enchantment);
        }
        result.sort(Comparator.comparing(
                enchantment -> BuiltInRegistries.ENCHANTMENT.getKey(enchantment).toString()));
        return result;
    }

    /** Number of offers for a tier: 3 / 4 / 5. */
    public static int offerCount(int tier) {
        return 3 + Math.max(0, Math.min(MAX_TIER, tier));
    }

    /**
     * The offers for this input, tier and seed. Deterministic in all three, and keyed by the exact item
     * type (registry id), so the same weapon type always yields the same group until the seed advances.
     * Levels roll in the tier's band (1-2 / 3-4 / 5-6) clamped to each enchantment's max; cost scales with
     * rarity and rolled level.
     */
    public static List<EnchantOffer> computeOffers(ItemStack stack, int tier, int seed) {
        List<Enchantment> candidates = availableEnchantments(stack);
        if (candidates.isEmpty()) {
            return List.of();
        }
        int clampedTier = Math.max(0, Math.min(MAX_TIER, tier));
        int count = offerCount(clampedTier);
        int lo = 1 + clampedTier * 2;
        int hi = lo + 1;

        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        long rngSeed = ((long) seed * 0x9E3779B97F4A7C15L) ^ ((long) itemKey.hashCode() * 0xC2B2AE3D27D4EB4FL);
        Random random = new Random(rngSeed);

        List<Enchantment> pool = new ArrayList<>(candidates);
        Collections.shuffle(pool, random);
        int shown = Math.min(count, pool.size());

        List<EnchantOffer> offers = new ArrayList<>(shown);
        for (int i = 0; i < shown; i++) {
            Enchantment enchantment = pool.get(i);
            int level = lo + random.nextInt(hi - lo + 1);
            level = Math.max(1, Math.min(level, enchantment.getMaxLevel()));
            int cost = rarityCost(enchantment) + (level - 1);
            offers.add(new EnchantOffer(enchantment, level, cost));
        }
        return offers;
    }

    /** Base point cost from an enchantment's rarity: common/uncommon 1, rare 2, very rare 3. */
    private static int rarityCost(Enchantment enchantment) {
        return switch (enchantment.getRarity()) {
            case RARE -> 2;
            case VERY_RARE -> 3;
            default -> 1;
        };
    }

    public static boolean hasEnchanterAdvancement(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        net.minecraft.advancements.Advancement advancement =
                server.getAdvancements().getAdvancement(ENCHANTER_ADVANCEMENT);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || !this.isNearLitCampfire()) {
            return false;
        }
        if (id == BACK_BUTTON) {
            if (CampfireHubMenu.hasSpentTonight(serverPlayer)) {
                serverPlayer.closeContainer();
            } else {
                CampfireHubMenu.openHub(serverPlayer, this.access, this.campfirePos);
            }
            return true;
        }
        if (CampfireHubMenu.hasSpentTonight(serverPlayer)) {
            return false;
        }
        ItemStack input = this.inputSlot.getItem();
        if (availableEnchantments(input).isEmpty()) {
            return false;
        }

        if (id == REROLL_BUTTON) {
            if (getEnchantmentPoints() < REROLL_COST) {
                serverPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.limen.enchant_no_point"), true);
                return false;
            }
            if (!consumeEnchantmentPoints(REROLL_COST)) {
                return false;
            }
            int newSeed = (short) (this.seedData.get() * 1664525 + 1013904223);
            persistentData(serverPlayer).putInt(SEED_KEY, newSeed);
            this.seedData.set(newSeed);
            playUseSound();
            this.broadcastChanges();
            return true;
        }

        List<EnchantOffer> offers = computeOffers(input, this.tierData.get(), this.seedData.get());
        if (id < 0 || id >= offers.size()) {
            return false;
        }
        EnchantOffer offer = offers.get(id);
        if (getEnchantmentPoints() < offer.cost()) {
            serverPlayer.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.limen.enchant_no_point"), true);
            return false;
        }
        if (!consumeEnchantmentPoints(offer.cost())) {
            return false;
        }
        input.enchant(offer.enchantment(), offer.level());
        this.inputSlot.setChanged();
        playUseSound();
        CampfireHubMenu.grantCraftReward(serverPlayer);
        this.broadcastChanges();
        return true;
    }

    private void playUseSound() {
        this.access.execute((level, pos) -> {
            long gameTime = level.getGameTime();
            if (this.lastSoundTime != gameTime) {
                level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.lastSoundTime = gameTime;
            }
        });
    }

    private boolean consumeEnchantmentPoints(int amount) {
        if (getEnchantmentPoints() < amount) {
            return false;
        }
        int remaining = amount;
        for (int i = 0; i < this.playerInventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = this.playerInventory.getItem(i);
            if (stack.is(ModItems.ENCHANTMENT_POINTS.get())) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    private static CompoundTag persistentData(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag data = root.getCompound(Player.PERSISTED_NBT_TAG);
        root.put(Player.PERSISTED_NBT_TAG, data);
        return data;
    }

    private boolean isNearLitCampfire() {
        return this.access.evaluate((level, pos) -> CampfireBlock.isLitCampfire(level.getBlockState(this.campfirePos)),
                true);
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

        if (index == INPUT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!availableEnchantments(stack).isEmpty()) {
            if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < MAIN_INVENTORY_END) {
            if (!this.moveItemStackTo(stack, MAIN_INVENTORY_END, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INVENTORY_START, MAIN_INVENTORY_END, false)) {
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
