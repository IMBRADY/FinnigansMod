package net.finnigan.tommemod.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class CombiningRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Ingredient ingredientA;
    private final Ingredient ingredientB;
    private final ItemStack result;
    private final int cookTime;

    public CombiningRecipe(ResourceLocation id, Ingredient ingredientA, Ingredient ingredientB,
                           ItemStack result, int cookTime) {
        this.id = id;
        this.ingredientA = ingredientA;
        this.ingredientB = ingredientB;
        this.result = result;
        this.cookTime = cookTime;
    }

    // Matches a 2-slot container regardless of which slot holds which ingredient
    @Override
    public boolean matches(Container container, Level level) {
        ItemStack slot0 = container.getItem(0);
        ItemStack slot1 = container.getItem(1);

        boolean straight = ingredientA.test(slot0) && ingredientB.test(slot1);
        boolean swapped = ingredientA.test(slot1) && ingredientB.test(slot0);
        return straight || swapped;
    }

    public Ingredient getIngredientA() { return ingredientA; }
    public Ingredient getIngredientB() { return ingredientB; }
    public int getCookTime() { return cookTime; }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredientA);
        list.add(ingredientB);
        return list;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.COMBINING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.COMBINING_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<CombiningRecipe> {
        @Override
        public CombiningRecipe fromJson(ResourceLocation id, com.google.gson.JsonObject json) {
            Ingredient ingredientA = Ingredient.fromJson(json.get("ingredient_a"));
            Ingredient ingredientB = Ingredient.fromJson(json.get("ingredient_b"));
            ItemStack result = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(
                    json.getAsJsonObject("result"));
            int cookTime = net.minecraft.util.GsonHelper.getAsInt(json, "cookingtime", 200);
            return new CombiningRecipe(id, ingredientA, ingredientB, result, cookTime);
        }

        @Override
        public CombiningRecipe fromNetwork(ResourceLocation id, net.minecraft.network.FriendlyByteBuf buf) {
            Ingredient ingredientA = Ingredient.fromNetwork(buf);
            Ingredient ingredientB = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            int cookTime = buf.readInt();
            return new CombiningRecipe(id, ingredientA, ingredientB, result, cookTime);
        }

        @Override
        public void toNetwork(net.minecraft.network.FriendlyByteBuf buf, CombiningRecipe recipe) {
            recipe.ingredientA.toNetwork(buf);
            recipe.ingredientB.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeInt(recipe.cookTime);
        }
    }
}