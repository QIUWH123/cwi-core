package net.quiwh.cwi.compat.kubejs;

import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.IgnoreComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance;
import java.util.List;
import net.quiwh.cwi.compat.recipevalue.RecipeValueAdapters;

/** Canonical Create setters retain native wrapper priority for mixed item/fluid lists. */
public final class CwiMachineRecipeSchemas {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register(RecipeSchemaRegistry registry) {
        for (String name : List.of("blast_furnace_processing", "mixing_vessel_mixing", "incubating")) {
            var schema = registry.namespace("cwi").getRegisteredOrThrow(name).schema;
            for (String field : List.of("ingredients", "results")) {
                RecipeKey key = schema.getKey(field);
                schema.function(new RecipeFunctionInstance(field, List.of(IgnoreComponent.INSTANCE), (context, arguments) -> {
                    Object value = RecipeValueAdapters.wrap(context, key, arguments.getFirst());
                    RecipeValueAdapters.limits(context, field, value);
                    context.recipe().setValue(key, value);
                }));
            }
            if (name.equals("mixing_vessel_mixing") || name.equals("blast_furnace_processing")) {
                RecipeKey<Integer> priorityKey = schema.getKey("priority");
                schema.function(new RecipeFunctionInstance("priority", List.of(IgnoreComponent.INSTANCE), (context, arguments) -> {
                    if (arguments.size() != 1 || !(arguments.getFirst() instanceof Number number)
                            || !Double.isFinite(number.doubleValue()) || number.doubleValue() != Math.rint(number.doubleValue())
                            || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE)
                        throw new IllegalArgumentException("priority expects a signed 32-bit integer");
                    context.recipe().setValue(priorityKey, number.intValue());
                }));
                RecipeKey<List<net.quiwh.cwi.machine.recipe.OutputPool>> poolsKey = schema.getKey("output_pools");
                schema.function(new RecipeFunctionInstance("outputPool", List.of(IgnoreComponent.INSTANCE), (context, arguments) -> {
                    if (arguments.size() != 1) throw new IllegalArgumentException("outputPool expects one array of item entries");
                    var pools = new java.util.ArrayList<net.quiwh.cwi.machine.recipe.OutputPool>(context.recipe().getValue(poolsKey));
                    if (pools.size() >= 64) throw new IllegalArgumentException("Maximum 64 output pools");
                    pools.add(RecipeValueAdapters.pool(context, arguments.getFirst())); context.recipe().setValue(poolsKey, pools);
                }));
                schema.function(new RecipeFunctionInstance("showOutputs", List.of(IgnoreComponent.INSTANCE), (context, arguments) -> {
                    if (arguments.size() > 1 || !arguments.isEmpty() && !(arguments.getFirst() instanceof Boolean))
                        throw new IllegalArgumentException("showOutputs expects no argument or a boolean");
                    var pools = new java.util.ArrayList<net.quiwh.cwi.machine.recipe.OutputPool>(context.recipe().getValue(poolsKey));
                    if (pools.isEmpty()) throw new IllegalArgumentException("showOutputs must follow outputPool");
                    var latest = pools.getLast(); pools.set(pools.size() - 1,
                            new net.quiwh.cwi.machine.recipe.OutputPool(latest.entries(), arguments.isEmpty() || (Boolean)arguments.getFirst()));
                    context.recipe().setValue(poolsKey, pools);
                }));
            }
        }
    }
    private CwiMachineRecipeSchemas() {}
}
