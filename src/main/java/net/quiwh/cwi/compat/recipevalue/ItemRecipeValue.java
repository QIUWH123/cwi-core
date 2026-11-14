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
    private ItemRecipeValue(Source source, ResourceLocation id, String snapshot, int count, boolean patch,
                    Double chance, Double weight, Boolean timing, net.quiwh.cwi.machine.recipe.EntrySettings settings) {
        super(source, id, snapshot, count, patch, chance, weight, timing, settings);
    }
    @Override ItemRecipeValue copy(Double chance, Double weight, Boolean timing) {
        return new ItemRecipeValue(source, id, snapshot, quantity, componentPatch, chance, weight, timing, settings);
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
    public ItemRecipeValue perTick() { return perTick(1); }
    public ItemRecipeValue perTick(Object interval) { return perTick(interval, "working"); }
    public ItemRecipeValue perTick(Object interval, Object clock) {
        var next = periodic(interval, clock, settings);
        return new ItemRecipeValue(source, id, snapshot, quantity, componentPatch, chance, weight, next.periodic(), next);
    }
    public ItemRecipeValue catalyst() {
        return new ItemRecipeValue(source, id, snapshot, quantity, componentPatch, chance, weight, timing,
                new net.quiwh.cwi.machine.recipe.EntrySettings(settings.interval(), settings.clock(), true, settings.damage(), settings.energy()));
    }
    public ItemRecipeValue damage(Object amount) { return toolCost(amount, true); }
    public ItemRecipeValue consumeEnergy(Object amount) { return toolCost(amount, false); }
    private ItemRecipeValue toolCost(Object raw, boolean damage) {
        int amount = RecipeValues.quantity(raw);
        return new ItemRecipeValue(source, id, snapshot, quantity, componentPatch, chance, weight, timing,
                new net.quiwh.cwi.machine.recipe.EntrySettings(settings.interval(), settings.clock(), settings.catalyst(),
                        damage ? amount : settings.damage(), damage ? settings.energy() : amount));
    }
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
