package com.moonlightc.limen.inventory;

import com.moonlightc.limen.block.TableSawBlock;
import com.moonlightc.limen.recipe.TableSawRecyclingRecipe;
import com.moonlightc.limen.registry.ModBlocks;
import com.moonlightc.limen.registry.ModMenuTypes;
import com.moonlightc.limen.registry.ModRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TableSawMenu extends AbstractContainerMenu {
    private static final int INPUT_SLOT = 0;
    private static final int RESULT_SLOT = 1;
    private static final int INVENTORY_START = 2;
    private static final int INVENTORY_END = 38;
    private static final int MAIN_INVENTORY_END = 29;

    private final ContainerLevelAccess access;
    private final Level level;
    private final DataSlot selectedIndex = DataSlot.standalone();
    private final ResultContainer resultContainer = new ResultContainer();
    private final Container inputContainer;
    private final Slot inputSlot;
    private List<Offer> offers = List.of();
    private ItemStack lastInput = ItemStack.EMPTY;
    private Runnable updateListener = () -> { };
    private long lastSoundTime;

    public TableSawMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory,
                ContainerLevelAccess.create(inventory.player.level(), data.readBlockPos()));
    }

    public TableSawMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenuTypes.TABLE_SAW.get(), containerId);
        this.access = access;
        this.level = inventory.player.level();
        this.inputContainer = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                TableSawMenu.this.slotsChanged(this);
                TableSawMenu.this.updateListener.run();
            }
        };

        this.inputSlot = this.addSlot(new Slot(this.inputContainer, INPUT_SLOT, 20, 33));
        this.addSlot(new ResultSlot(this.resultContainer, 0, 143, 33));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        this.addDataSlot(this.selectedIndex);
    }

    public void registerUpdateListener(Runnable listener) {
        this.updateListener = listener;
    }

    public int getSelectedRecipeIndex() {
        return this.selectedIndex.get();
    }

    public int getNumRecipes() {
        return this.offers.size();
    }

    public boolean hasInputItem() {
        return this.inputSlot.hasItem() && !this.offers.isEmpty();
    }

    public ItemStack getOfferResult(int index) {
        return index >= 0 && index < this.offers.size()
                ? this.offers.get(index).result().copy()
                : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.isPoweredAndPresent()
                && AbstractContainerMenu.stillValid(this.access, player, ModBlocks.TABLE_SAW.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!this.isPoweredAndPresent() || id < 0 || id >= this.offers.size()) {
            return false;
        }
        this.selectedIndex.set(id);
        this.setupResultSlot();
        return true;
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack input = this.inputSlot.getItem();
        if (!ItemStack.isSameItemSameTags(input, this.lastInput)) {
            this.lastInput = input.copy();
            this.setupOfferList(input);
        } else {
            this.lastInput.setCount(input.getCount());
            this.setupResultSlot();
        }
    }

    private void setupOfferList(ItemStack input) {
        this.selectedIndex.set(-1);
        this.resultContainer.clearContent();
        if (input.isEmpty()) {
            this.offers = List.of();
            this.broadcastChanges();
            return;
        }

        Map<String, Offer> candidates = new LinkedHashMap<>();
        for (StonecutterRecipe recipe : this.level.getRecipeManager()
                .getRecipesFor(RecipeType.STONECUTTING, this.inputContainer, this.level)) {
            ItemStack result = recipe.assemble(this.inputContainer, this.level.registryAccess());
            if (!result.isEmpty()) {
                Offer offer = new Offer("forward:" + recipe.getId(), result, 1, recipe, null);
                candidates.putIfAbsent(offer.key(), offer);
            }
        }

        List<TableSawRecyclingRecipe> explicit = this.level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.TABLE_SAW_RECYCLING_TYPE.get()).stream()
                .filter(recipe -> recipe.getIngredient().test(input))
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
                .toList();
        if (!explicit.isEmpty()) {
            for (TableSawRecyclingRecipe recipe : explicit) {
                Offer offer = new Offer("explicit:" + recipe.getId(),
                        recipe.getResultItem(this.level.registryAccess()).copy(),
                        recipe.getInputCount(), recipe, null);
                candidates.putIfAbsent(offer.key(), offer);
            }
        } else {
            this.addInferredOffers(input, candidates);
        }

        List<Offer> sorted = new ArrayList<>(candidates.values());
        sorted.sort(Comparator.comparing(Offer::key));
        this.offers = List.copyOf(sorted);
        this.broadcastChanges();
    }

    private void addInferredOffers(ItemStack input, Map<String, Offer> candidates) {
        if (!(input.getItem() instanceof BlockItem)) {
            return;
        }
        List<StonecutterRecipe> recipes = new ArrayList<>(
                this.level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING));
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        for (StonecutterRecipe recipe : recipes) {
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            if (!(result.getItem() instanceof BlockItem) || !result.is(input.getItem()) || result.getCount() < 1
                    || recipe.getIngredients().isEmpty()) {
                continue;
            }
            for (ItemStack source : recipe.getIngredients().get(0).getItems()) {
                if (!(source.getItem() instanceof BlockItem) || source.getCount() != 1) {
                    continue;
                }
                ItemStack restored = new ItemStack(source.getItem());
                Item inputItem = input.getItem();
                ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(source.getItem());
                String key = "inferred:" + sourceId + ":" + result.getCount();
                candidates.putIfAbsent(key,
                        new Offer(key, restored, result.getCount(), null, inputItem));
            }
        }
    }

    private void setupResultSlot() {
        this.resultContainer.clearContent();
        this.resultContainer.setRecipeUsed(null);
        if (!this.isPoweredAndPresent()) {
            this.broadcastChanges();
            return;
        }
        int index = this.selectedIndex.get();
        if (index >= 0 && index < this.offers.size()) {
            Offer offer = this.offers.get(index);
            ItemStack input = this.inputSlot.getItem();
            if (input.getCount() >= offer.inputCount()
                    && this.isOfferValid(offer, input)
                    && offer.result().isItemEnabled(this.level.enabledFeatures())) {
                if (offer.recipe() != null) {
                    this.resultContainer.setRecipeUsed(offer.recipe());
                }
                this.resultContainer.setItem(0, offer.result().copy());
            }
        }
        this.broadcastChanges();
    }

    private boolean isOfferValid(Offer offer, ItemStack input) {
        if (offer.recipe() instanceof TableSawRecyclingRecipe recyclingRecipe) {
            return recyclingRecipe.getIngredient().test(input);
        }
        if (offer.recipe() instanceof StonecutterRecipe stonecutterRecipe) {
            return stonecutterRecipe.matches(this.inputContainer, this.level);
        }
        if (offer.inferredInput() != null) {
            return input.is(offer.inferredInput()) && input.getCount() >= offer.inputCount();
        }
        return false;
    }

    private boolean isPoweredAndPresent() {
        return this.access.evaluate((level, pos) -> {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            return state.is(ModBlocks.TABLE_SAW.get()) && state.getValue(TableSawBlock.POWERED);
        }, false);
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
            if (!this.isPoweredAndPresent() || !this.canMoveCompletely(stack, INVENTORY_START, INVENTORY_END)) {
                return ItemStack.EMPTY;
            }
            ItemStack crafted = stack.copy();
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
            if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, crafted);
            return original;
        } else if (index == INPUT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.hasAnyOffer(stack)) {
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
        this.broadcastChanges();
        return original;
    }

    private boolean canMoveCompletely(ItemStack stack, int startIndex, int endIndex) {
        int remaining = stack.getCount();
        for (int index = startIndex; index < endIndex && remaining > 0; index++) {
            Slot target = this.slots.get(index);
            ItemStack targetStack = target.getItem();
            if (!target.mayPlace(stack)) {
                continue;
            }
            if (targetStack.isEmpty()) {
                remaining -= Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize());
            } else if (ItemStack.isSameItemSameTags(stack, targetStack)) {
                int maximum = Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize());
                remaining -= Math.max(0, maximum - targetStack.getCount());
            }
        }
        return remaining <= 0;
    }

    private boolean hasAnyOffer(ItemStack stack) {
        SimpleContainer testContainer = new SimpleContainer(stack);
        if (!this.level.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, testContainer, this.level).isEmpty()) {
            return true;
        }
        if (this.level.getRecipeManager().getAllRecipesFor(ModRecipes.TABLE_SAW_RECYCLING_TYPE.get()).stream()
                .anyMatch(recipe -> recipe.getIngredient().test(stack))) {
            return true;
        }
        if (!(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        return this.level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING).stream()
                .map(recipe -> recipe.getResultItem(this.level.registryAccess()))
                .anyMatch(result -> result.is(stack.getItem()) && result.getItem() instanceof BlockItem);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.clearContent();
        this.clearContainer(player, this.inputContainer);
    }

    private record Offer(String key, ItemStack result, int inputCount, @Nullable Recipe<?> recipe,
                         @Nullable Item inferredInput) {
    }

    private final class ResultSlot extends Slot {
        private ResultSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return TableSawMenu.this.isPoweredAndPresent() && super.mayPickup(player);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            int index = TableSawMenu.this.selectedIndex.get();
            if (index < 0 || index >= TableSawMenu.this.offers.size()) {
                return;
            }
            Offer offer = TableSawMenu.this.offers.get(index);
            ItemStack input = TableSawMenu.this.inputSlot.getItem();
            if (input.getCount() < offer.inputCount() || !TableSawMenu.this.isOfferValid(offer, input)) {
                return;
            }

            stack.onCraftedBy(player.level(), player, stack.getCount());
            if (offer.recipe() != null) {
                TableSawMenu.this.resultContainer.awardUsedRecipes(player, List.of(input.copy()));
            }
            TableSawMenu.this.inputSlot.remove(offer.inputCount());
            TableSawMenu.this.setupResultSlot();
            TableSawMenu.this.access.execute((level, pos) -> {
                long gameTime = level.getGameTime();
                if (TableSawMenu.this.lastSoundTime != gameTime) {
                    level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    TableSawMenu.this.lastSoundTime = gameTime;
                }
            });
            super.onTake(player, stack);
        }
    }
}



