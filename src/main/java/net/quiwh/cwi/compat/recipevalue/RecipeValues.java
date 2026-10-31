package net.quiwh.cwi.compat.recipevalue;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import java.util.Set;
import java.util.regex.Pattern;

public final class RecipeValues {
    private static final Pattern COUNT = Pattern.compile("([0-9]+)x\\s+(.+)");
    // Registered, data-only native codecs. Callback-backed KubeJS ingredients stay native.
    private static final Set<String> PREDICATES = Set.of("neoforge:compound", "neoforge:intersection",
            "neoforge:difference", "neoforge:components", "neoforge:simple", "neoforge:tag", "neoforge:empty");
    public static Object unwrap(Object raw) { return Wrapper.unwrapped(raw); }
    static int quantity(Object value) {
        double n = RecipeValue.number(value, "Quantity");
        if (n <= 0 || n > Integer.MAX_VALUE || n != Math.rint(n))
            throw new IllegalArgumentException("Quantity must be a positive 32-bit integer");
        return (int)n;
    }
    private static <T> String snapshot(Context cx, Codec<T> codec, T value, boolean predicate) {
        var ops = RegistryAccessContainer.of(cx).json();
        JsonElement encoded = codec.encodeStart(ops, value).getOrThrow().deepCopy();
        if (predicate) verifyPredicate(encoded);
        codec.parse(ops, encoded.deepCopy()).getOrThrow();
        return encoded.toString();
    }
    private static void verifyPredicate(JsonElement json) {
        if (json.isJsonArray()) { json.getAsJsonArray().forEach(RecipeValues::verifyPredicate); return; }
        if (!json.isJsonObject()) return;
        var object = json.getAsJsonObject();
        if (object.has("type") && !PREDICATES.contains(object.get("type").getAsString()))
            throw new IllegalArgumentException("Ingredient codec has no verified immutable copy contract: " + object.get("type"));
        // Component payloads may themselves contain a type property unrelated to ingredients.
        for (String key : new String[]{"ingredients", "children", "base", "subtracted", "ingredient"})
            if (object.has(key)) verifyPredicate(object.get(key));
    }
    public static RecipeValue create(Context cx, boolean fluid, Object[] args) {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Item/Fluid expects a value and optional quantity");
        Object raw = unwrap(args[0]);
        Integer explicit = args.length == 2 ? quantity(unwrap(args[1])) : null;
        if (raw instanceof RecipeValue old) {
            if (fluid != old.fluid()) throw new IllegalArgumentException("Cannot copy an opposite-kind descriptor");
            if (explicit != null) throw new IllegalArgumentException("Descriptor already supplies a quantity");
            return old.copy(old.chance, old.weight, old.timing);
        }
        var source = RecipeValue.Source.ID;
        ResourceLocation id = null; String data = null; int count = fluid ? 0 : 1; boolean sized = false, patch = false;
        if (raw instanceof CharSequence sequence) {
            String text = sequence.toString().trim();
            boolean alias = text.startsWith("#") && COUNT.matcher(text.substring(1)).matches();
            if (alias) text = text.substring(1);
            var match = COUNT.matcher(text);
            if (match.matches()) {
                try { count = quantity(Long.parseLong(match.group(1))); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Quantity exceeds positive 32-bit range"); }
                sized = true; text = match.group(2);
            }
            if (alias || text.startsWith("#")) { source = RecipeValue.Source.TAG; if (!alias) text = text.substring(1); }
            if (text.isEmpty() || text.contains(" ") || text.contains("#") || !text.equals(text.trim()))
                throw new IllegalArgumentException("Malformed item/fluid ID or unknown counted syntax: " + sequence);
            id = ResourceLocation.tryParse(text);
            if (id == null) throw new IllegalArgumentException("Malformed item/fluid ID: " + text);
        } else if (!fluid && raw instanceof ItemStack stack) {
            if (stack.isEmpty()) throw new IllegalArgumentException("Empty item stack");
            source = RecipeValue.Source.STACK; count = quantity(stack.getCount()); sized = true;
            patch = !stack.getComponentsPatch().isEmpty(); data = snapshot(cx, ItemStack.CODEC, stack.copyWithCount(1), false);
        } else if (fluid && raw instanceof FluidStack stack) {
            if (stack.isEmpty()) throw new IllegalArgumentException("Empty fluid stack");
            source = RecipeValue.Source.STACK; count = quantity(stack.getAmount()); sized = true;
            patch = !stack.getComponentsPatch().isEmpty(); data = snapshot(cx, FluidStack.CODEC, stack.copyWithAmount(1), false);
        } else if (!fluid && (raw instanceof Ingredient || raw instanceof SizedIngredient)) {
            source = RecipeValue.Source.INGREDIENT;
            Ingredient ingredient;
            if (raw instanceof SizedIngredient value) { ingredient = value.ingredient(); count = quantity(value.count()); sized = true; }
            else ingredient = (Ingredient)raw;
            data = snapshot(cx, Ingredient.CODEC_NONEMPTY, ingredient, true);
        } else if (fluid && (raw instanceof FluidIngredient || raw instanceof SizedFluidIngredient)) {
            source = RecipeValue.Source.INGREDIENT;
            FluidIngredient ingredient;
            if (raw instanceof SizedFluidIngredient value) { ingredient = value.ingredient(); count = quantity(value.amount()); sized = true; }
            else ingredient = (FluidIngredient)raw;
            data = snapshot(cx, FluidIngredient.CODEC_NON_EMPTY, ingredient, true);
        } else throw new IllegalArgumentException("Expected an ID, descriptor or supported native stack/ingredient; arrays and raw objects are ambiguous");
        if (explicit != null) {
            if (sized) throw new IllegalArgumentException("Quantity declared twice");
            count = explicit;
        }
        if (count == 0) throw new IllegalArgumentException("Fluid requires an explicit amount");
        return fluid ? new FluidRecipeValue(source, id, data, count, patch, null, null)
                : new ItemRecipeValue(source, id, data, count, patch, null, null, null);
    }
    private RecipeValues() {}
}
