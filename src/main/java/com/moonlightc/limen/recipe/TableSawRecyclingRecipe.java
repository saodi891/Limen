package com.moonlightc.limen.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.moonlightc.limen.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public final class TableSawRecyclingRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final int inputCount;
    private final ItemStack result;

    public TableSawRecyclingRecipe(ResourceLocation id, Ingredient ingredient, int inputCount, ItemStack result) {
        this.id = id;
        this.ingredient = ingredient;
        this.inputCount = inputCount;
        this.result = result;
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack stack = container.getItem(0);
        return this.ingredient.test(stack) && stack.getCount() >= this.inputCount;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(this.ingredient);
        return ingredients;
    }

    public Ingredient getIngredient() {
        return this.ingredient;
    }

    public int getInputCount() {
        return this.inputCount;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TABLE_SAW_RECYCLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.TABLE_SAW_RECYCLING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<TableSawRecyclingRecipe> {
        @Override
        public TableSawRecyclingRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
            if (ingredient.isEmpty()) {
                throw new JsonParseException("Table saw recycling ingredient must not be empty");
            }

            int inputCount = GsonHelper.getAsInt(json, "input_count", 1);
            if (inputCount < 1 || inputCount > 64) {
                throw new JsonParseException("Table saw recycling input_count must be between 1 and 64");
            }

            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            if (result.isEmpty() || result.getCount() < 1 || result.getCount() > result.getMaxStackSize()) {
                throw new JsonParseException("Table saw recycling result count is invalid");
            }
            return new TableSawRecyclingRecipe(id, ingredient, inputCount, result);
        }

        @Override
        public TableSawRecyclingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int inputCount = buffer.readVarInt();
            ItemStack result = buffer.readItem();
            if (ingredient.isEmpty() || inputCount < 1 || inputCount > 64 || result.isEmpty()
                    || result.getCount() > result.getMaxStackSize()) {
                throw new IllegalArgumentException("Invalid table saw recycling recipe received from network");
            }
            return new TableSawRecyclingRecipe(id, ingredient, inputCount, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, TableSawRecyclingRecipe recipe) {
            recipe.ingredient.toNetwork(buffer);
            buffer.writeVarInt(recipe.inputCount);
            buffer.writeItem(recipe.result);
        }
    }
}
