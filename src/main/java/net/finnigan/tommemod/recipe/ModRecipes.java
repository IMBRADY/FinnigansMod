package net.finnigan.tommemod.recipe;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TommeMod.MOD_ID);

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.RECIPE_TYPES, TommeMod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<CombiningRecipe>> COMBINING_SERIALIZER =
            SERIALIZERS.register("combining", CombiningRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<CombiningRecipe>> COMBINING_TYPE =
            TYPES.register("combining", () -> new RecipeType<CombiningRecipe>() {
                @Override
                public String toString() {
                    return TommeMod.MOD_ID + ":combining";
                }
            });

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
        TYPES.register(eventBus);
    }
}