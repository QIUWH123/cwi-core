package net.quiwh.cwi.compat;

import com.mojang.datafixers.util.Either;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.ComponentValueMap;
import dev.latvian.mods.kubejs.recipe.component.IgnoreComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeConstructor;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaType;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance;
import dev.latvian.mods.kubejs.util.IntBounds;
import net.quiwh.cwi.machine.recipe.MachineRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Standard output/input arguments write directly to the native, independently editable IO keys. */
public final class CwiMachineRecipeSchemas {
    public static void register(RecipeSchemaRegistry registry) {
        var namespace = registry.namespace("cwi");
        var itemInput = CwiMachineRecipeComponents.ITEM_INPUT.instance();
        var fluidInput = CwiMachineRecipeComponents.FLUID_INPUT.instance();
        var itemOutput = CwiMachineRecipeComponents.ITEM_OUTPUT.instance();
        var fluidOutput = CwiMachineRecipeComponents.FLUID_OUTPUT.instance();
        var furnace = namespace.getRegisteredOrThrow("blast_furnace_processing").schema;
        install(furnace, mixed(furnace, "inputs", "item_inputs", "fluid_inputs", itemInput, fluidInput),
                single(furnace, "outputs", "fluid_outputs", fluidOutput));
        var incubator = namespace.getRegisteredOrThrow("incubating").schema;
        install(incubator, single(incubator, "inputs", "item_inputs", itemInput),
                single(incubator, "outputs", "item_outputs", itemOutput));
        var mixer = namespace.getRegisteredOrThrow("mixing_vessel_mixing").schema;
        install(mixer, mixed(mixer, "inputs", "item_inputs", "fluid_inputs", itemInput, fluidInput),
                mixed(mixer, "outputs", "item_outputs", "fluid_outputs", itemOutput, fluidOutput));
    }

    private record Side<T>(RecipeKey<List<T>> argument, BiConsumer<RecipeScriptContext, List<T>> setter) {
        void set(RecipeScriptContext context, Object raw) {
            setter.accept(context, net.quiwh.cwi.compat.recipevalue.RecipeValueAdapters.wrap(context, argument, raw));
        }
    }

    private static <T> Side<T> single(RecipeSchema schema, String name, String nativeName, RecipeComponent<T> component) {
        RecipeKey<List<T>> nativeKey = schema.getKey(nativeName);
        var argument = component.asListOrSelf().withBounds(IntBounds.of(0, MachineRecipe.MAX_ENTRIES)).otherKey(name);
        return new Side<>(argument, (context, entries) -> {
            requireLimit(entries);
            context.recipe().setValue(nativeKey, entries);
        });
    }

    private static <I, F> Side<Either<I, F>> mixed(RecipeSchema schema, String name, String itemName, String fluidName,
                                                 RecipeComponent<I> itemComponent, RecipeComponent<F> fluidComponent) {
        RecipeKey<List<I>> itemKey = schema.getKey(itemName);
        RecipeKey<List<F>> fluidKey = schema.getKey(fluidName);
        var argument = itemComponent.or(fluidComponent).asListOrSelf()
                .withBounds(IntBounds.of(0, MachineRecipe.MAX_ENTRIES * 2)).otherKey(name);
        return new Side<>(argument, (context, entries) -> {
            var items = new ArrayList<I>();
            var fluids = new ArrayList<F>();
            for (var entry : entries) entry.ifLeft(items::add).ifRight(fluids::add);
            requireLimit(items);
            requireLimit(fluids);
            context.recipe().setValue(itemKey, items);
            context.recipe().setValue(fluidKey, fluids);
        });
    }

    private static <I, O> void install(RecipeSchema schema, Side<I> inputs, Side<O> outputs) {
        // Argument keys belong only to this constructor; saved recipes retain the native IO keys.
        schema.constructor(new RecipeConstructor(outputs.argument(), inputs.argument()) {
            @Override public void setValues(RecipeScriptContext context, RecipeSchemaType type, ComponentValueMap values) {
                outputs.setter().accept(context, values.getValue(context, outputs.argument()));
                inputs.setter().accept(context, values.getValue(context, inputs.argument()));
            }
        });
        // Schema functions convert by TypeInfo, rather than component.wrap. Preserve the raw
        // JS value here so Side.set can distinguish native fluid stacks from item ingredients.
        for (String name : List.of("input", "inputs"))
            schema.function(new RecipeFunctionInstance(name, List.of(IgnoreComponent.INSTANCE),
                    (context, arguments) -> inputs.set(context, arguments.getFirst())));
        for (String name : List.of("output", "outputs"))
            schema.function(new RecipeFunctionInstance(name, List.of(IgnoreComponent.INSTANCE),
                    (context, arguments) -> outputs.set(context, arguments.getFirst())));
    }

    private static void requireLimit(List<?> entries) {
        if (entries.size() > MachineRecipe.MAX_ENTRIES)
            throw new IllegalArgumentException("Machine recipes allow at most " + MachineRecipe.MAX_ENTRIES + " entries per item/fluid channel");
    }

    private CwiMachineRecipeSchemas() {}
}
