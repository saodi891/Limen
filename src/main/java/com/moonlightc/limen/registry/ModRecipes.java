package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.recipe.TableSawRecyclingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, Limen.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Limen.MODID);

    public static final RegistryObject<RecipeType<TableSawRecyclingRecipe>> TABLE_SAW_RECYCLING_TYPE =
            RECIPE_TYPES.register("table_saw_recycling", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return Limen.id("table_saw_recycling").toString();
                }
            });

    public static final RegistryObject<RecipeSerializer<TableSawRecyclingRecipe>> TABLE_SAW_RECYCLING_SERIALIZER =
            RECIPE_SERIALIZERS.register("table_saw_recycling", TableSawRecyclingRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }
}
