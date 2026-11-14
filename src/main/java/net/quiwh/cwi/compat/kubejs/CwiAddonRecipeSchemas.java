package net.quiwh.cwi.compat.kubejs;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.schema.*;
import dev.latvian.mods.kubejs.util.IntBounds;
import dev.latvian.mods.kubejs.util.TickDuration;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Optional native recipe builders and narrow corrections to upstream schema keys. */
public final class CwiAddonRecipeSchemas {
    public static final RecipeComponentType<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> FLUID_INPUT = RecipeComponentType.unit(id("create_fluid_ingredient"),
            type -> new SizedFluidIngredientComponent(type, com.simibubi.create.foundation.codec.CreateCodecs.FLAT_SIZED_FLUID_INGREDIENT_WITH_TYPE, false));
    public static final RecipeComponentType<HeatCondition> HEAT = RecipeComponentType.unit(id("heat_condition"),
            type -> new ValueComponent<>(type, HeatCondition.CODEC, TypeInfo.of(HeatCondition.class)));

    public record ValueComponent<T>(RecipeComponentType<?> type, Codec<T> codec, TypeInfo typeInfo) implements RecipeComponent<T> {}

    public static void registerComponents(RecipeComponentTypeRegistry registry) {
        registry.register(HEAT);
        registry.register(FLUID_INPUT);
    }

    /** Called by the schema event after every mod plugin, once per server script load/reload. */
    public static void register(RecipeSchemaRegistry registry) {
        assemblyVisibility(registry);
        hazardProtection(registry);
        // Item application has exactly two item ingredients. The inherited processing
        // schema tries fluid tags first, which turns an axe tag into a fluid input.
        patch(registry, "create", "item_application", "ingredients",
                IngredientComponent.INGREDIENT.instance().asListOrSelf().withBounds(IntBounds.of(2, 2)), false);
        allowEmptyList(registry, "create", "item_application", "results", false);
        CwiApplicationRecipeSchemas.register(registry);
        if (loaded("createaddition")) {
            register(registry, "createaddition", "rolling", processing(false));
            var charging = processing(false, NumberComponent.INT.otherKey("energy"), NumberComponent.INT.otherKey("max_charge_rate"));
            // Required energy/rate follow the conventional output/input arguments, before optional fields.
            register(registry, "createaddition", "charging", charging);
            var ingredients = FLUID_INPUT.instance().asListOrSelf().inputKey("ingredients");
            var burn = NumberComponent.INT.otherKey("burn_time");
            var results = FluidStackComponent.FLUID_STACK.instance().asList().withBounds(IntBounds.of(0, 0))
                    .outputKey("results").optional(List.of()).alwaysWrite().exclude();
            register(registry, "createaddition", "liquid_burning", new RecipeSchema(ingredients, burn,
                    BooleanComponent.BOOLEAN.otherKey("superheated").optional(false), results));
        }
        if (loaded("createdieselgenerators")) for (String name : List.of("hammering", "wire_cutting", "distillation", "basin_fermenting", "bulk_fermenting"))
            register(registry, "createdieselgenerators", name, processing(!name.equals("hammering") && !name.equals("wire_cutting")));
        if (loaded("tfmg")) {
            register(registry, "tfmg", "coking", processing(true));
            register(registry, "tfmg", "distillation", processing(true));
            var vat = processing(true);
            vat = append(vat, NumberComponent.NON_NEGATIVE_INT.otherKey("min_size").optional(0),
                    NumberComponent.INT.otherKey("heat_level").optional(0), NumberComponent.INT.otherKey("pressure").optional(0),
                    StringComponent.ID.instance().asListOrSelf().withBounds(IntBounds.of(0, Integer.MAX_VALUE)).otherKey("machines").optional(List.of()),
                    StringComponent.ID.instance().asListOrSelf().withBounds(IntBounds.of(0, Integer.MAX_VALUE)).otherKey("allowed_vat_types").optional(List.of()));
            register(registry, "tfmg", "vat_machine_recipe", vat);
            // 1.3.3 uses vat as the native type and retains vat_machine_recipe as an alias.
            if (BuiltInRegistries.RECIPE_SERIALIZER.containsKey(ResourceLocation.fromNamespaceAndPath("tfmg", "vat")))
                register(registry, "tfmg", "vat", vat);
        }
        if (loaded("ratatouille")) for (String name : List.of("threshing", "demolding")) register(registry, "ratatouille", name, processing(!name.equals("demolding")));
        if (loaded("vintageimprovements")) for (String name : List.of("pressurizing", "leaves_vibrating", "auto_upgrade", "auto_smithing"))
            allowEmptyList(registry, "vintageimprovements", name, "results", false);
        if (loaded("createmetallurgy")) allowEmptyList(registry, "createmetallurgy", "entity_melting", "ingredients", true);
        CwiNativeProcessingSchemas.register(registry);
        patch(registry, "minecraft", "smithing_transform", "template", IngredientComponent.OPTIONAL_INGREDIENT.instance(), false);
    }

    private static void hazardProtection(RecipeSchemaRegistry registry) {
        for (String name : List.of("fluid_hazard", "inventory_hazard")) {
            var original = registry.namespace("cwi").get(name).schema;
            var schema = append(original);
            for (var constructor : original.constructors().values()) schema.constructor(constructor);
            RecipeKey<String> protection = schema.getKey("protection");
            RecipeKey<Integer> resistance = schema.getKey("required_resistance");
            schema.function(new dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance("protection", List.of(IgnoreComponent.INSTANCE),
                    (context, args) -> {
                        if (args.isEmpty() || args.size() > 2 || !(args.getFirst() instanceof CharSequence))
                            throw new IllegalArgumentException("protection expects a protection ID and optional required resistance");
                        context.recipe().setValue(protection, ResourceLocation.parse(args.getFirst().toString()).toString());
                        if (args.size() == 2) {
                            if (!(args.get(1) instanceof Number n) || !Double.isFinite(n.doubleValue())
                                    || n.doubleValue() != Math.rint(n.doubleValue()) || n.doubleValue() < 1 || n.doubleValue() > 16)
                                throw new IllegalArgumentException("Required resistance must be an integer in [1,16]");
                            context.recipe().setValue(resistance, ((Number) args.get(1)).intValue());
                        }
                    }));
            registry.namespace("cwi").register(name, schema);
        }
    }

    private static void assemblyVisibility(RecipeSchemaRegistry registry) {
        var type = registry.namespace("create").get("sequenced_assembly");
        if (type == null || type instanceof UnknownRecipeSchemaType) return;
        var visible = BooleanComponent.BOOLEAN.otherKey("show_outputs").optional(false).exclude();
        var schema = append(type.schema, visible);
        if (!type.schema.constructorsGenerated()) for (var constructor : type.schema.constructors().values())
            schema.constructor(constructor);
        schema.function(new dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance("showOutputs", List.of(IgnoreComponent.INSTANCE),
                (context, arguments) -> {
                    if (arguments.size() > 1 || !arguments.isEmpty() && !(arguments.getFirst() instanceof Boolean))
                        throw new IllegalArgumentException("showOutputs expects no argument or a boolean");
                    context.recipe().setValue(visible, arguments.isEmpty() || (Boolean) arguments.getFirst());
                }));
        registry.namespace("create").register("sequenced_assembly", schema);
    }

    static RecipeSchema processing(boolean timeRequired, RecipeKey<?>... extraRequired) {
        var output = FluidStackComponent.FLUID_STACK.instance().or(CwiProcessingOutputComponent.TYPE.instance())
                .asListOrSelf().outputKey("results");
        var input = IngredientComponent.INGREDIENT.instance().or(FLUID_INPUT.instance())
                .asListOrSelf().withSpread(Optional.of(SizedIngredientComponent.SIZED_INGREDIENT.instance().or(IgnoreComponent.INSTANCE)))
                .inputKey("ingredients");
        var duration = TimeComponent.TICKS.inputKey("processing_time").optional(TickDuration.of(100));
        if (timeRequired) duration.alwaysWrite();
        var heat = HEAT.otherKey("heat_requirement").optional(HeatCondition.NONE).exclude();
        var keys = new ArrayList<RecipeKey<?>>(List.of(output, input));
        keys.addAll(List.of(extraRequired)); keys.add(duration); keys.add(heat);
        return new RecipeSchema(Map.of(), keys).uniqueId(output)
                .setOpFunction("heated", heat, HeatCondition.HEATED).setOpFunction("superheated", heat, HeatCondition.SUPERHEATED);
    }

    private static RecipeSchema append(RecipeSchema schema, RecipeKey<?>... keys) {
        var list = new ArrayList<>(schema.keys); list.addAll(List.of(keys));
        var copy = new RecipeSchema(schema.keyOverrides, list).factory(schema.recipeFactory).typeOverride(schema.typeOverride).uniqueIds(schema.uniqueIds());
        copy.functions.putAll(schema.functions); schema.postProcessors().forEach(copy::postProcessor);
        return copy;
    }

    private static void register(RecipeSchemaRegistry registry, String namespace, String name, RecipeSchema schema) {
        // Do not replace a future dedicated addon/plugin schema or a user-supplied datapack schema.
        var existing = registry.namespace(namespace).get(name);
        if (existing == null || existing instanceof UnknownRecipeSchemaType) registry.namespace(namespace).register(name, schema);
    }

    private static void allowEmptyList(RecipeSchemaRegistry registry, String namespace, String name, String keyName, boolean optional) {
        var type = registry.namespace(namespace).get(name);
        if (type != null && type.schema.getOptionalKey(keyName) instanceof RecipeKey<?> key && key.component instanceof ListRecipeComponent<?> list)
            patch(registry, namespace, name, keyName, list.withBounds(IntBounds.of(0, list.bounds().max())), optional);
    }

    private static <T> void patch(RecipeSchemaRegistry registry, String namespace, String name, String keyName, RecipeComponent<T> component, boolean optionalEmptyList) {
        var type = registry.namespace(namespace).get(name);
        if (type == null || type instanceof UnknownRecipeSchemaType) return;
        RecipeKey<T> old = type.schema.getKey(keyName);
        var key = new RecipeKey<>(component, old.name, old.role);
        key.names.addAll(old.names); key.optional = old.optional; key.excluded = old.excluded;
        key.functionNames = old.functionNames; key.alwaysWrite = old.alwaysWrite;
        if (optionalEmptyList) { @SuppressWarnings("unchecked") T empty = (T) List.of(); key.optional(empty).alwaysWrite(); }
        var schema = type.schema;
        var keys = new ArrayList<>(schema.keys); keys.set(keys.indexOf(old), key);
        var overrides = new IdentityHashMap<RecipeKey<?>, RecipeOptional<?>>();
        schema.keyOverrides.forEach((k, v) -> overrides.put(k == old ? key : k, v));
        var copy = new RecipeSchema(overrides, keys).factory(schema.recipeFactory).typeOverride(schema.typeOverride)
                .uniqueIds(schema.uniqueIds().stream().map(k -> k == old ? key : k).toList());
        // The affected schemas' custom functions reference untouched keys (heat/time), retaining their native factories.
        copy.functions.putAll(schema.functions); schema.postProcessors().forEach(copy::postProcessor);
        if (!schema.constructorsGenerated()) for (var constructor : schema.constructors().values()) {
            var replacement = new RecipeConstructor(constructor.keys.stream().map(k -> k == old ? key : k).toList());
            replacement.overrides = remap(constructor.overrides, old, key);
            replacement.defaultValues = remap(constructor.defaultValues, old, key);
            copy.constructor(replacement);
        }
        registry.namespace(namespace).register(name, copy);
    }

    private static Map<RecipeKey<?>, RecipeOptional<?>> remap(Map<RecipeKey<?>, RecipeOptional<?>> values, RecipeKey<?> old, RecipeKey<?> key) {
        var copy = new IdentityHashMap<RecipeKey<?>, RecipeOptional<?>>();
        values.forEach((k, v) -> copy.put(k == old ? key : k, v)); return copy;
    }
    private static boolean loaded(String id) { return ModList.get().isLoaded(id); }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("cwi", name); }
    private CwiAddonRecipeSchemas() {}
}
