package net.quiwh.cwi.compat.recipevalue;

import com.mojang.datafixers.util.Either;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import dev.latvian.mods.kubejs.error.KubeRuntimeException;
import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.quiwh.cwi.machine.recipe.MachineRecipe.*;
import java.lang.reflect.Array;
import java.util.*;

/** Field-specific adaptation. Native components, codecs and execution are never replaced. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class RecipeValueAdapters {
    private static final Set<String> CORE = Set.of("cwi:mixing_vessel_mixing", "cwi:blast_furnace_processing", "cwi:incubating");
    private static final Set<String> CREATE = Set.of("create:mixing", "create:filling", "create:deploying", "create:pressing", "create:sequenced_assembly");
    public static boolean contains(Object raw) { return contains(raw, Collections.newSetFromMap(new IdentityHashMap<>())); }
    private static boolean contains(Object raw, Set<Object> seen) {
        raw = RecipeValues.unwrap(raw);
        if (raw instanceof RecipeValue) return true;
        if (raw == null || !seen.add(raw)) return false;
        if (raw instanceof Map<?, ?> map) return map.values().stream().anyMatch(v -> contains(v, seen));
        if (raw instanceof Iterable<?> values) { for (Object v : values) if (contains(v, seen)) return true; }
        else if (raw.getClass().isArray()) for (int i = 0; i < Array.getLength(raw); i++) if (contains(Array.get(raw, i), seen)) return true;
        return false;
    }
    static String type(RecipeScriptContext cx) { return cx.recipe().type.idString; }
    private static KubeRuntimeException error(RecipeScriptContext cx, String field, int index, String why) {
        return (KubeRuntimeException)new KubeRuntimeException(type(cx) + " recipe " + (cx.recipe().id == null ? "<pending ID>" : cx.recipe().id) + " field " + field
                + (index < 0 ? "" : "[" + index + "]") + ": " + why).source(cx.recipe().sourceLine);
    }
    public static <T> T wrap(RecipeScriptContext cx, RecipeKey<T> key, Object raw) {
        return wrap(cx, key.name, key.component, raw);
    }
    public static <T> T wrap(RecipeScriptContext cx, String field, RecipeComponent<T> component, Object raw) {
        if (!contains(raw)) return component.wrap(cx, raw);
        try {
            requireField(cx, field);
            Object result = adapt(cx, field, component, raw, -1);
            limits(cx, field, result);
            return (T)result;
        } catch (KubeRuntimeException ex) { throw ex; }
        catch (RuntimeException ex) { throw error(cx, field, -1, ex.getMessage()); }
    }
    private static void requireField(RecipeScriptContext cx, String field) {
        String type = type(cx);
        boolean supported = CORE.contains(type) ? switch (field) {
            case "inputs", "outputs", "item_inputs" -> true;
            case "fluid_inputs", "fluid_outputs" -> !type.equals("cwi:incubating");
            case "item_outputs" -> !type.equals("cwi:blast_furnace_processing");
            default -> false;
        } : CREATE.contains(type) && (type.equals("create:sequenced_assembly")
                ? Set.of("ingredient", "results", "transitional_item").contains(field)
                : Set.of("ingredients", "results").contains(field));
        if (!supported) throw error(cx, field, -1, "descriptors are not supported in this recipe field");
    }
    private static List<Object> entries(Object raw) {
        raw = RecipeValues.unwrap(raw);
        var result = new ArrayList<Object>();
        if (raw instanceof Iterable<?> values) { for (Object v : values) result.add(RecipeValues.unwrap(v)); }
        else if (raw != null && raw.getClass().isArray()) for (int i = 0; i < Array.getLength(raw); i++) result.add(RecipeValues.unwrap(Array.get(raw, i)));
        else result.add(raw);
        return result;
    }
    private static Object adapt(RecipeScriptContext cx, String field, RecipeComponent component, Object raw, int index) {
        raw = RecipeValues.unwrap(raw);
        if (component instanceof ListRecipeComponent list) {
            var values = entries(raw);
            int maximum = CORE.contains(type(cx)) ? 128 : type(cx).equals("create:sequenced_assembly") ? Integer.MAX_VALUE : 66;
            if (values.size() > maximum) throw error(cx, field, -1, "too many entries (limit " + maximum + ")");
            var result = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (value instanceof RecipeValue descriptor) {
                    int repetitions = type(cx).equals("create:mixing") && field.equals("ingredients") && !descriptor.fluid() ? descriptor.quantity : 1;
                    if (repetitions > 64 || result.size() + (long)repetitions > maximum)
                        throw error(cx, field, i, "count spreading exceeds the native ingredient limit");
                    Object entry = adapt(cx, field, list.component(), descriptor, i);
                    for (int n = 0; n < repetitions; n++) result.add(entry);
                } else if (contains(value)) throw error(cx, field, i, "descriptors must be complete entries, not embedded raw objects");
                else {
                    // Inspect the native sized representation before its spreading can allocate.
                    if (list.spreadWrap().isPresent()) {
                        Object sized = ((RecipeComponent)list.spreadWrap().get()).wrap(cx, value);
                        Object unwrapped = sized instanceof Either e ? e.map(v -> v, v -> v) : sized;
                        if (unwrapped instanceof SizedIngredient ingredient && ingredient.count() > 64)
                            throw error(cx, field, i, "count spreading exceeds the native ingredient limit");
                    }
                    result.addAll(list.wrap(cx, List.of(value)));
                }
            }
            return result;
        }
        if (!(raw instanceof RecipeValue value)) {
            if (contains(raw)) throw error(cx, field, index, "descriptors must be complete entries");
            return component.wrap(cx, raw);
        }
        try {
            if (component instanceof EitherRecipeComponent either) {
                // Choose by descriptor kind before native branch priority or TypeInfo conversion.
                boolean leftFluid = either.left().toString().contains("fluid");
                RecipeComponent branch = value.fluid() == leftFluid ? either.left() : either.right();
                Object entry = adapt(cx, field, branch, value, index);
                return branch == either.left() ? Either.left(entry) : Either.right(entry);
            }
            boolean componentFluid = component.toString().contains("fluid");
            if (componentFluid != value.fluid()) throw new IllegalArgumentException("wrong descriptor kind for this item/fluid channel");
            Object adapted = component.wrap(cx, nativeEntry(cx, field, value));
            // Enforce the destination codec before a setter or replacement can write it.
            component.codec().encodeStart(cx.ops().json(), adapted).getOrThrow();
            return adapted;
        } catch (KubeRuntimeException ex) { throw ex; }
        catch (RuntimeException ex) { throw error(cx, field, index, ex.getMessage()); }
    }
    private static Object nativeEntry(RecipeScriptContext cx, String field, RecipeValue value) {
        boolean output = field.equals("outputs") || field.endsWith("outputs") || field.equals("results") || field.equals("transitional_item");
        String type = type(cx);
        if (CORE.contains(type)) {
            if (value.weight != null) throw new IllegalArgumentException("Core machine entries do not support weight");
            if (type.equals("cwi:incubating") && (value.fluid() || (!output && (value.quantity != 1 || value.timing != null))))
                throw new IllegalArgumentException("Incubating requires one count-1 item input without timing metadata; fluids are unsupported");
            if (type.equals("cwi:blast_furnace_processing") && output && !value.fluid())
                throw new IllegalArgumentException("Blast furnace outputs are fluid-only");
            float chance = value.chance == null ? 1 : value.chance.floatValue();
            if (value.chance != null && value.chance > 0 && chance == 0) throw new IllegalArgumentException("Chance underflows the native float range");
            boolean tick = Boolean.TRUE.equals(value.timing);
            if (value instanceof ItemRecipeValue item) return output ? new ItemOutput(item.stack(cx.cx()), chance, tick)
                    : new ItemInput(item.ingredient(cx.cx()), item.quantity, chance, tick);
            FluidRecipeValue fluid = (FluidRecipeValue)value;
            return output ? new FluidOutput(fluid.stack(cx.cx()), chance, tick)
                    : new FluidInput(new SizedFluidIngredient(fluid.ingredient(cx.cx()), fluid.quantity), chance, tick);
        }
        if (value.timing != null) throw new IllegalArgumentException("Create fields do not support explicit timing, including perTick(false)");
        if (!output) {
            if (value.chance != null || value.weight != null) throw new IllegalArgumentException("Create ingredients do not support chance or weight");
            if (value instanceof FluidRecipeValue fluid) {
                if (!type.equals("create:mixing") && !type.equals("create:filling")) throw new IllegalArgumentException("This Create recipe does not support fluid ingredients");
                return new SizedFluidIngredient(fluid.ingredient(cx.cx()), fluid.quantity);
            }
            if (value.quantity != 1 && !type.equals("create:mixing")) throw new IllegalArgumentException("This item ingredient requires count 1");
            return ((ItemRecipeValue)value).ingredient(cx.cx());
        }
        if (value instanceof FluidRecipeValue fluid) {
            if (!type.equals("create:mixing")) throw new IllegalArgumentException("This Create recipe does not support fluid results");
            if (value.chance != null || value.weight != null) throw new IllegalArgumentException("Create fluid results do not support chance or weight");
            return fluid.stack(cx.cx());
        }
        ItemRecipeValue item = (ItemRecipeValue)value;
        boolean assembly = type.equals("create:sequenced_assembly");
        if (field.equals("transitional_item")) {
            if (item.metadata() || item.quantity != 1) throw new IllegalArgumentException("Transitional item requires count 1 and no metadata");
        } else if (assembly && value.chance != null) throw new IllegalArgumentException("Assembly final results use withWeight, not withChance");
        else if (!assembly && value.weight != null) throw new IllegalArgumentException("Ordinary Create results do not support weight");
        if (item.quantity > 99) throw new IllegalArgumentException("Create result count must be in 1..99");
        double number = assembly ? (value.weight == null ? 1 : value.weight) : (value.chance == null ? 1 : value.chance);
        float nativeNumber = (float)number;
        if (!Float.isFinite(nativeNumber) || nativeNumber <= 0) throw new IllegalArgumentException("Create chance/weight must remain a positive finite native float (zero, underflow and overflow are unsupported)");
        return new ProcessingOutput(item.stack(cx.cx()), nativeNumber);
    }
    private static Object unwrapEither(Object value) { return value instanceof Either e ? e.map(v -> v, v -> v) : value; }
    private static boolean fluid(Object value) {
        value = unwrapEither(value);
        return value instanceof FluidStack || value instanceof SizedFluidIngredient || value instanceof FluidInput || value instanceof FluidOutput;
    }
    public static void limits(RecipeScriptContext cx, String field, Object value) {
        List<Object> list = entries(value);
        int fluids = 0; for (Object entry : list) if (fluid(entry)) fluids++;
        int items = list.size() - fluids;
        String type = type(cx);
        if (CORE.contains(type)) {
            if (items > 64 || fluids > 64) throw error(cx, field, -1, "limit is 64 entries per item/fluid channel");
            if (type.equals("cwi:incubating") && (field.equals("inputs") || field.equals("item_inputs")) && items != 1)
                throw error(cx, field, -1, "Incubating requires exactly one item input");
        } else if (field.equals("ingredients")) {
            boolean okay = switch(type) {
                case "create:mixing" -> items <= 64 && fluids <= 2;
                case "create:filling" -> items == 1 && fluids == 1;
                case "create:deploying" -> items == 2 && fluids == 0;
                case "create:pressing" -> items == 1 && fluids == 0;
                default -> false;
            };
            if (!okay) throw error(cx, field, -1, "ingredient counts exceed or do not match this native recipe's item/fluid slots");
        } else if (field.equals("results")) {
            int max = switch(type) { case "create:mixing", "create:deploying" -> 4; case "create:filling" -> 1; case "create:pressing" -> 2; default -> Integer.MAX_VALUE; };
            if (items > max || fluids > (type.equals("create:mixing") ? 2 : 0)) throw error(cx, field, -1, "result entries exceed this native recipe's limits");
            if (type.equals("create:sequenced_assembly")) {
                float sum = 0;
                for (Object entry : list) sum += ((ProcessingOutput)unwrapEither(entry)).getChance();
                if (!Float.isFinite(sum) || sum <= 0) throw error(cx, field, -1, "assembly weights must have a finite positive ordered native float total");
            }
        }
    }
    /** Descriptor replacement replaces a complete matched entry, before any legacy same-ID shortcut. */
    public static Object replace(RecipeScriptContext cx, String field, RecipeComponent component, Object original,
                                 ReplacementMatchInfo match, RecipeValue replacement) {
        if (original == null) return null;
        if (component instanceof ListRecipeComponent list) {
            List<?> values = (List<?>)original;
            var result = new ArrayList<Object>();
            boolean changed = false;
            for (int i = 0; i < values.size(); i++) {
                Object old = values.get(i);
                if (!list.component().matches(cx, old, match)) { result.add(old); continue; }
                requireField(cx, field);
                int repetitions = type(cx).equals("create:mixing") && field.equals("ingredients") && !replacement.fluid() ? replacement.quantity : 1;
                if (repetitions > 64 || result.size() + (long)repetitions > (CORE.contains(type(cx)) ? 128 : type(cx).equals("create:sequenced_assembly") ? Integer.MAX_VALUE : 66))
                    throw error(cx, field, i, "replacement count exceeds native ingredient/channel limits");
                Object entry = adapt(cx, field, list.component(), replacement, i);
                for (int n = 0; n < repetitions; n++) result.add(entry);
                changed = true;
            }
            if (!changed) return original;
            limits(cx, field, result);
            return result;
        }
        if (!component.matches(cx, original, match)) return original;
        return wrap(cx, field, component, replacement);
    }
    private RecipeValueAdapters() {}
}
