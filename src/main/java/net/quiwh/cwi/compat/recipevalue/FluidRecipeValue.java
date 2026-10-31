package net.quiwh.cwi.compat.recipevalue;

import dev.latvian.mods.rhino.Context;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

public final class FluidRecipeValue extends RecipeValue {
    FluidRecipeValue(Source source, ResourceLocation id, String snapshot, int amount, boolean patch,
                     Double chance, Boolean timing) {
        super(source, id, snapshot, amount, patch, chance, null, timing);
    }
    @Override FluidRecipeValue copy(Double chance, Double weight, Boolean timing) {
        return new FluidRecipeValue(source, id, snapshot, quantity, componentPatch, chance, timing);
    }
    @Override boolean fluid() { return true; }
    public FluidRecipeValue withChance(Object probability) { return copy(probability(probability), null, timing); }
    public FluidRecipeValue perTick() { return perTick(true); }
    public FluidRecipeValue perTick(Object enabled) { return copy(chance, null, bool(enabled)); }
    public FluidStack toStack(Context cx) { requireStack(); return stack(cx); }
    FluidStack stack(Context cx) {
        if (source == Source.TAG || source == Source.INGREDIENT)
            throw new IllegalArgumentException("Fluid outputs require a concrete fluid, not a tag or ingredient predicate");
        FluidStack result;
        if (source == Source.STACK) result = decode(cx, FluidStack.CODEC);
        else {
            if (!BuiltInRegistries.FLUID.containsKey(id) || id.equals(ResourceLocation.withDefaultNamespace("empty")))
                throw new IllegalArgumentException("Unknown or empty fluid: " + id);
            result = new FluidStack(BuiltInRegistries.FLUID.get(id), 1);
        }
        result.setAmount(quantity);
        return result;
    }
    FluidIngredient ingredient(Context cx) {
        if (componentPatch) throw new IllegalArgumentException("Component-bearing fluid stacks are output-only; wrap an explicit native fluid predicate for inputs");
        FluidIngredient ingredient = switch (source) {
            case INGREDIENT -> decode(cx, FluidIngredient.CODEC_NON_EMPTY);
            case TAG -> FluidIngredient.tag(TagKey.create(Registries.FLUID, id));
            default -> FluidIngredient.single(stack(cx).getFluid());
        };
        if (ingredient.hasNoFluids()) throw new IllegalArgumentException("Fluid ingredient/tag must contain a registered fluid: " + this);
        return ingredient;
    }
}
