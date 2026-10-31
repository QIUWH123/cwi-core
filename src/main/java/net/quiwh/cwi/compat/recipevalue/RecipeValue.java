package net.quiwh.cwi.compat.recipevalue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.Context;
import net.minecraft.resources.ResourceLocation;

/** An immutable recipe entry; deliberately not an inventory stack or a JSON object. */
public abstract sealed class RecipeValue permits ItemRecipeValue, FluidRecipeValue {
    enum Source { ID, TAG, STACK, INGREDIENT }
    final Source source;
    final ResourceLocation id;
    final String snapshot;
    final int quantity;
    final boolean componentPatch;
    final Double chance, weight;
    final Boolean timing;

    RecipeValue(Source source, ResourceLocation id, String snapshot, int quantity, boolean componentPatch,
                Double chance, Double weight, Boolean timing) {
        this.source = source; this.id = id; this.snapshot = snapshot; this.quantity = quantity;
        this.componentPatch = componentPatch; this.chance = chance; this.weight = weight; this.timing = timing;
    }
    abstract RecipeValue copy(Double chance, Double weight, Boolean timing);
    abstract boolean fluid();
    final boolean metadata() { return chance != null || weight != null || timing != null; }
    final void requireStack() {
        if (source == Source.TAG || source == Source.INGREDIENT || metadata())
            throw new IllegalArgumentException("toStack() requires a concrete value without explicit recipe metadata");
    }
    static double probability(Object raw) {
        double n = number(raw, "Chance");
        if (n < 0 || n > 1) throw new IllegalArgumentException("Chance must be in 0..1");
        return n;
    }
    static double number(Object raw, String name) {
        raw = RecipeValues.unwrap(raw);
        if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()))
            throw new IllegalArgumentException(name + " must be a finite number");
        return n.doubleValue();
    }
    static boolean bool(Object raw) {
        raw = RecipeValues.unwrap(raw);
        if (!(raw instanceof Boolean b)) throw new IllegalArgumentException("perTick expects a boolean");
        return b;
    }
    final <T> T decode(Context cx, Codec<T> codec) {
        return codec.parse(RegistryAccessContainer.of(cx).json(), JsonParser.parseString(snapshot)).getOrThrow();
    }
    @Override public final String toString() {
        return (fluid() ? "Fluid(" : "Item(") + quantity + "x " + (source == Source.TAG ? "#" : "")
                + (id == null ? "native " + source.name().toLowerCase() : id) + ")";
    }
}
