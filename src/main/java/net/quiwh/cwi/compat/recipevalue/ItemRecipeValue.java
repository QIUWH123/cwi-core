package net.quiwh.cwi.compat.recipevalue;

import dev.latvian.mods.rhino.Context;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;

public final class ItemRecipeValue extends RecipeValue {
    ItemRecipeValue(Source source, ResourceLocation id, String snapshot, int count, boolean patch,
                    Double chance, Double weight, Boolean timing) {
        super(source, id, snapshot, count, patch, chance, weight, timing);
    }
    @Override ItemRecipeValue copy(Double chance, Double weight, Boolean timing) {
        return new ItemRecipeValue(source, id, snapshot, quantity, componentPatch, chance, weight, timing);
    }
    @Override boolean fluid() { return false; }
    public ItemRecipeValue withChance(Object probability) {
        if (weight != null) throw new IllegalArgumentException("Chance and weight are mutually exclusive");
        return copy(probability(probability), null, timing);
    }
    public ItemRecipeValue withWeight(Object weight) {
        if (chance != null) throw new IllegalArgumentException("Chance and weight are mutually exclusive");
        double n = number(weight, "Weight");
        if (n <= 0) throw new IllegalArgumentException("Weight must be positive");
        return copy(null, n, timing);
    }
    public ItemRecipeValue perTick() { return perTick(true); }
    public ItemRecipeValue perTick(Object enabled) { return copy(chance, weight, bool(enabled)); }
    public ItemStack toStack(Context cx) { requireStack(); return stack(cx); }
    ItemStack stack(Context cx) {
        if (source == Source.TAG || source == Source.INGREDIENT)
            throw new IllegalArgumentException("Item outputs require a concrete item, not a tag or ingredient predicate");
        ItemStack result;
        if (source == Source.STACK) result = decode(cx, ItemStack.CODEC);
        else {
            if (!BuiltInRegistries.ITEM.containsKey(id) || id.equals(ResourceLocation.withDefaultNamespace("air")))
                throw new IllegalArgumentException("Unknown or empty item: " + id);
            result = new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        result.setCount(quantity);
        return result;
    }
    Ingredient ingredient(Context cx) {
        if (componentPatch) throw new IllegalArgumentException("Component-bearing stacks are output-only; wrap an explicit native ingredient predicate for inputs");
        Ingredient ingredient = switch (source) {
            case INGREDIENT -> decode(cx, Ingredient.CODEC_NONEMPTY);
            case TAG -> Ingredient.of(TagKey.create(Registries.ITEM, id));
            default -> Ingredient.of(stack(cx).copyWithCount(1));
        };
        if (ingredient.hasNoItems()) throw new IllegalArgumentException("Item ingredient/tag must contain a registered item: " + this);
        return ingredient;
    }
}
