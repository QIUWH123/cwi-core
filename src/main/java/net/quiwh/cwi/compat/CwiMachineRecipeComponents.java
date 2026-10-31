package net.quiwh.cwi.compat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import dev.latvian.mods.kubejs.fluid.FluidWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.ItemWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.SizedIngredientWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.FluidIngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.FluidStackComponent;
import dev.latvian.mods.kubejs.recipe.component.IngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.quiwh.cwi.machine.recipe.MachineRecipe.FluidInput;
import net.quiwh.cwi.machine.recipe.MachineRecipe.FluidOutput;
import net.quiwh.cwi.machine.recipe.MachineRecipe.ItemInput;
import net.quiwh.cwi.machine.recipe.MachineRecipe.ItemOutput;
import net.quiwh.cwi.machine.recipe.IncubationDisplay;

import java.util.ArrayList;
import java.util.Map;

/** KubeJS uses its normal item/tag/fluid wrappers while the saved JSON uses native codecs. */
public final class CwiMachineRecipeComponents {
    public static final RecipeComponentType<ItemInput> ITEM_INPUT = RecipeComponentType.unit(id("machine_item_input"), ItemInputComponent::new);
    public static final RecipeComponentType<FluidInput> FLUID_INPUT = RecipeComponentType.unit(id("machine_fluid_input"), FluidInputComponent::new);
    public static final RecipeComponentType<ItemOutput> ITEM_OUTPUT = RecipeComponentType.unit(id("machine_item_output"), ItemOutputComponent::new);
    public static final RecipeComponentType<FluidOutput> FLUID_OUTPUT = RecipeComponentType.unit(id("machine_fluid_output"), FluidOutputComponent::new);
    public static final RecipeComponentType<IncubationDisplay> INCUBATION_DISPLAY = RecipeComponentType.unit(id("incubation_display"), IncubationDisplayComponent::new);

    public static void register(RecipeComponentTypeRegistry registry) {
        registry.register(ITEM_INPUT); registry.register(FLUID_INPUT);
        registry.register(ITEM_OUTPUT); registry.register(FLUID_OUTPUT);
        registry.register(INCUBATION_DISPLAY);
    }

    private record IncubationDisplayComponent(RecipeComponentType<?> type) implements RecipeComponent<IncubationDisplay> {
        @Override public Codec<IncubationDisplay> codec() { return IncubationDisplay.CODEC; }
        @Override public TypeInfo typeInfo() { return TypeInfo.of(IncubationDisplay.class); }
        @Override public IncubationDisplay wrap(RecipeScriptContext context, Object value) {
            value = unwrap(value);
            if (value instanceof IncubationDisplay display) return display;
            if (value instanceof Map<?, ?> map) {
                var steps = new ArrayList<IncubationDisplay.Step>();
                Object rawSteps = unwrap(map.get("steps"));
                if (!(rawSteps instanceof Iterable<?> iterable)) throw new IllegalArgumentException("Incubation display steps must be an array");
                for (Object rawStep : iterable) {
                    Object unwrapped = unwrap(rawStep);
                    if (!(unwrapped instanceof Map<?, ?> step)) throw new IllegalArgumentException("Preparation step must be an object");
                    String type = String.valueOf(step.get("type"));
                    int count = positiveNumber(step.get("count"), 1);
                    if (type.equals("deploying")) {
                        if (step.containsKey("fluid")) throw new IllegalArgumentException("Deploying steps cannot specify fluid");
                        steps.add(new IncubationDisplay.Step(IncubationDisplay.Type.DEPLOYING,
                                wrapDisplayItem(context, step.get("item")), FluidStack.EMPTY, count));
                    } else if (type.equals("filling")) {
                        if (step.containsKey("item")) throw new IllegalArgumentException("Filling steps cannot specify an item");
                        FluidStack fluid = wrapFluidStack(context, step.get("fluid"));
                        if (step.containsKey("amount")) fluid = fluid.copyWithAmount(positiveNumber(step.get("amount"), 1000));
                        steps.add(new IncubationDisplay.Step(IncubationDisplay.Type.FILLING, ItemStack.EMPTY, fluid, count));
                    } else throw new IllegalArgumentException("Unknown preparation step type: " + type);
                }
                return new IncubationDisplay(wrapDisplayItem(context, map.get("microbe")), steps,
                        positiveNumber(map.get("yield_count"), 1));
            }
            return IncubationDisplay.CODEC.parse(context.ops().json(), JsonUtils.objectOf(context.cx(), value)).getOrThrow();
        }
        @Override public boolean isEmpty(IncubationDisplay value) { return value.microbe().isEmpty(); }
    }

    private record ItemInputComponent(RecipeComponentType<?> type) implements RecipeComponent<ItemInput> {
        @Override public Codec<ItemInput> codec() { return ItemInput.CODEC; }
        @Override public TypeInfo typeInfo() { return TypeInfo.of(ItemInput.class); }
        @Override public boolean hasPriority(RecipeMatchContext context, Object value) { return !isFluid(value); }
        @Override public ItemInput wrap(RecipeScriptContext context, Object value) {
            value = unwrap(value);
            if (value instanceof ItemInput input) return input;
            if (value instanceof SizedIngredient input) return new ItemInput(input.ingredient(), input.count(), 1, false);
            if (value instanceof ItemStack stack) return new ItemInput(Ingredient.of(stack), stack.getCount(), 1, false);
            if (value instanceof Map<?, ?> map && map.containsKey("ingredient"))
                return new ItemInput(IngredientWrapper.wrap(context.cx(), map.get("ingredient")),
                        positiveNumber(map.get("count"), 1), number(map.get("chance"), 1), bool(map.get("per_tick")));
            if (structured(value)) {
                JsonObject json = JsonUtils.objectOf(context.cx(), value);
                if (json.has("ingredient")) return new ItemInput(IngredientWrapper.wrap(context.cx(), json.get("ingredient")),
                        integer(json, "count", 1), chance(json), perTick(json));
                if (json.has("count") || json.has("chance") || json.has("per_tick")) {
                    JsonObject ingredient = json.deepCopy(); ingredient.remove("count"); ingredient.remove("chance"); ingredient.remove("per_tick");
                    return new ItemInput(IngredientWrapper.wrap(context.cx(), ingredient), integer(json, "count", 1), chance(json), perTick(json));
                }
            }
            SizedIngredient input = SizedIngredientWrapper.wrap(context.cx(), value);
            return new ItemInput(input.ingredient(), input.count(), 1, false);
        }
        @Override public boolean matches(RecipeMatchContext context, ItemInput value, ReplacementMatchInfo match) {
            return IngredientComponent.INGREDIENT.instance().matches(context, value.ingredient(), match);
        }
        @Override public ItemInput replace(RecipeScriptContext context, ItemInput value, ReplacementMatchInfo match, Object replacement) {
            if (!matches(context, value, match)) return value;
            Object raw = unwrap(replacement);
            if (explicit(raw)) return wrap(context, raw);
            return new ItemInput(IngredientWrapper.wrap(context.cx(), raw), value.count(), value.chance(), value.perTick());
        }
        @Override public boolean isEmpty(ItemInput value) { return value.ingredient().isEmpty(); }
        @Override public void buildUniqueId(UniqueIdBuilder builder, ItemInput value) {
            IngredientComponent.INGREDIENT.instance().buildUniqueId(builder, value.ingredient());
        }
    }

    private record FluidInputComponent(RecipeComponentType<?> type) implements RecipeComponent<FluidInput> {
        @Override public Codec<FluidInput> codec() { return FluidInput.CODEC; }
        @Override public TypeInfo typeInfo() { return TypeInfo.of(FluidInput.class); }
        @Override public boolean hasPriority(RecipeMatchContext context, Object value) { return isFluid(value); }
        @Override public FluidInput wrap(RecipeScriptContext context, Object value) {
            value = unwrap(value);
            if (value instanceof FluidInput input) return input;
            if (value instanceof SizedFluidIngredient ingredient) return new FluidInput(ingredient, 1, false);
            if (value instanceof FluidStack stack) return new FluidInput(SizedFluidIngredient.of(stack), 1, false);
            if (value instanceof Map<?, ?> map && map.containsKey("ingredient"))
                return new FluidInput(new SizedFluidIngredient(wrapFluidIngredient(context, map.get("ingredient")),
                        positiveNumber(map.get("amount"), 1000)), number(map.get("chance"), 1), bool(map.get("per_tick")));
            if (structured(value)) {
                JsonObject json = JsonUtils.objectOf(context.cx(), value);
                if (json.has("ingredient")) return new FluidInput(new SizedFluidIngredient(fluidIngredient(context, json.get("ingredient")),
                        integer(json, "amount", 1000)), chance(json), perTick(json));
                JsonObject ingredient = json.deepCopy(); ingredient.remove("amount"); ingredient.remove("chance"); ingredient.remove("per_tick");
                return new FluidInput(new SizedFluidIngredient(fluidIngredient(context, ingredient), integer(json, "amount", 1000)), chance(json), perTick(json));
            }
            return new FluidInput(FluidWrapper.wrapSizedIngredient(context.cx(), value), 1, false);
        }
        @Override public boolean matches(RecipeMatchContext context, FluidInput value, ReplacementMatchInfo match) {
            return FluidIngredientComponent.FLUID_INGREDIENT.instance().matches(context, value.ingredient().ingredient(), match);
        }
        @Override public FluidInput replace(RecipeScriptContext context, FluidInput value, ReplacementMatchInfo match, Object replacement) {
            if (!matches(context, value, match)) return value;
            Object raw = unwrap(replacement);
            if (explicit(raw)) return wrap(context, raw);
            return new FluidInput(new SizedFluidIngredient(FluidWrapper.wrapIngredient(context.cx(), raw), value.ingredient().amount()), value.chance(), value.perTick());
        }
        @Override public boolean isEmpty(FluidInput value) { return value.ingredient().ingredient().isEmpty(); }
        @Override public void buildUniqueId(UniqueIdBuilder builder, FluidInput value) {
            FluidIngredientComponent.FLUID_INGREDIENT.instance().buildUniqueId(builder, value.ingredient().ingredient());
        }
    }

    private record ItemOutputComponent(RecipeComponentType<?> type) implements RecipeComponent<ItemOutput> {
        @Override public Codec<ItemOutput> codec() { return ItemOutput.CODEC; }
        @Override public TypeInfo typeInfo() { return TypeInfo.of(ItemOutput.class); }
        @Override public boolean hasPriority(RecipeMatchContext context, Object value) { return !isFluid(value); }
        @Override public ItemOutput wrap(RecipeScriptContext context, Object value) {
            value = unwrap(value);
            if (value instanceof ItemOutput output) return output;
            if (value instanceof ProcessingOutput output) return new ItemOutput(output.getStack(), output.getChance(), false);
            if (value instanceof Map<?, ?> map && map.containsKey("stack")) return new ItemOutput(ItemWrapper.wrap(context.cx(), map.get("stack")),
                    number(map.get("chance"), 1), bool(map.get("per_tick")));
            if (structured(value)) {
                JsonObject json = JsonUtils.objectOf(context.cx(), value);
                if (json.has("stack")) return new ItemOutput(ItemWrapper.wrap(context.cx(), json.get("stack")), chance(json), perTick(json));
                JsonObject stack = stripMetadata(json);
                if (!stack.has("id") && stack.has("item")) stack.add("id", stack.remove("item"));
                return new ItemOutput(ItemWrapper.wrap(context.cx(), stack), chance(json), perTick(json));
            }
            return new ItemOutput(ItemWrapper.wrap(context.cx(), value), 1, false);
        }
        @Override public boolean matches(RecipeMatchContext context, ItemOutput value, ReplacementMatchInfo match) {
            return ItemStackComponent.ITEM_STACK.instance().matches(context, value.stack(), match);
        }
        @Override public ItemOutput replace(RecipeScriptContext context, ItemOutput value, ReplacementMatchInfo match, Object replacement) {
            if (!matches(context, value, match)) return value;
            Object raw = unwrap(replacement);
            if (explicit(raw)) return wrap(context, raw);
            return new ItemOutput(ItemWrapper.wrap(context.cx(), raw).copyWithCount(value.stack().getCount()), value.chance(), value.perTick());
        }
        @Override public boolean isEmpty(ItemOutput value) { return value.stack().isEmpty(); }
        @Override public void buildUniqueId(UniqueIdBuilder builder, ItemOutput value) { ItemStackComponent.ITEM_STACK.instance().buildUniqueId(builder, value.stack()); }
    }

    private record FluidOutputComponent(RecipeComponentType<?> type) implements RecipeComponent<FluidOutput> {
        @Override public Codec<FluidOutput> codec() { return FluidOutput.CODEC; }
        @Override public TypeInfo typeInfo() { return TypeInfo.of(FluidOutput.class); }
        @Override public boolean hasPriority(RecipeMatchContext context, Object value) { return isFluid(value); }
        @Override public FluidOutput wrap(RecipeScriptContext context, Object value) {
            value = unwrap(value);
            if (value instanceof FluidOutput output) return output;
            if (value instanceof Map<?, ?> map && map.containsKey("stack")) return new FluidOutput(wrapFluidStack(context, map.get("stack")),
                    number(map.get("chance"), 1), bool(map.get("per_tick")));
            if (structured(value)) {
                JsonObject json = JsonUtils.objectOf(context.cx(), value);
                if (json.has("stack")) return new FluidOutput(fluidStack(context, json.get("stack")), chance(json), perTick(json));
                return new FluidOutput(fluidStack(context, stripMetadata(json)), chance(json), perTick(json));
            }
            return new FluidOutput(FluidWrapper.wrap(context.cx(), value), 1, false);
        }
        @Override public boolean matches(RecipeMatchContext context, FluidOutput value, ReplacementMatchInfo match) {
            return FluidStackComponent.FLUID_STACK.instance().matches(context, value.stack(), match);
        }
        @Override public FluidOutput replace(RecipeScriptContext context, FluidOutput value, ReplacementMatchInfo match, Object replacement) {
            if (!matches(context, value, match)) return value;
            Object raw = unwrap(replacement);
            if (explicit(raw)) return wrap(context, raw);
            return new FluidOutput(FluidWrapper.wrap(context.cx(), raw).copyWithAmount(value.stack().getAmount()), value.chance(), value.perTick());
        }
        @Override public boolean isEmpty(FluidOutput value) { return value.stack().isEmpty(); }
        @Override public void buildUniqueId(UniqueIdBuilder builder, FluidOutput value) { FluidStackComponent.FLUID_STACK.instance().buildUniqueId(builder, value.stack()); }
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("cwi", name); }
    private static ItemStack wrapDisplayItem(RecipeScriptContext context, Object value) {
        value = unwrap(value);
        return structured(value) ? ItemStack.CODEC.parse(context.ops().json(), JsonUtils.objectOf(context.cx(), value)).getOrThrow()
                : ItemWrapper.wrap(context.cx(), value);
    }
    private static FluidIngredient wrapFluidIngredient(RecipeScriptContext context, Object value) {
        value = unwrap(value);
        return structured(value) ? fluidIngredient(context, JsonUtils.objectOf(context.cx(), value))
                : FluidWrapper.wrapIngredient(context.cx(), value);
    }
    private static FluidStack wrapFluidStack(RecipeScriptContext context, Object value) {
        value = unwrap(value);
        return structured(value) ? fluidStack(context, JsonUtils.objectOf(context.cx(), value))
                : FluidWrapper.wrap(context.cx(), value);
    }
    private static FluidIngredient fluidIngredient(RecipeScriptContext context, com.google.gson.JsonElement json) {
        return json.isJsonPrimitive() ? FluidWrapper.wrapIngredient(context.cx(), json.getAsString())
                : FluidIngredient.CODEC_NON_EMPTY.parse(context.ops().json(), json).getOrThrow();
    }
    private static FluidStack fluidStack(RecipeScriptContext context, com.google.gson.JsonElement json) {
        return json.isJsonPrimitive() ? FluidWrapper.wrap(context.cx(), json.getAsString())
                : FluidStack.CODEC.parse(context.ops().json(), json).getOrThrow();
    }
    private static Object unwrap(Object value) { while (value instanceof Wrapper wrapper) value = wrapper.unwrap(); return value; }
    private static boolean isFluid(Object value) {
        value = unwrap(value);
        if (value instanceof FluidStack || value instanceof Fluid || value instanceof FluidIngredient
                || value instanceof SizedFluidIngredient || value instanceof FluidInput || value instanceof FluidOutput) return true;
        if (value instanceof Map<?, ?> map)
            return map.containsKey("amount") || map.containsKey("fluid") || isFluid(map.get("stack")) || isFluid(map.get("ingredient"));
        if (value instanceof JsonObject json)
            return json.has("amount") || json.has("fluid") || isFluid(json.get("stack")) || isFluid(json.get("ingredient"));
        return false;
    }
    private static boolean structured(Object value) { return value instanceof Map<?, ?> || value instanceof JsonObject; }
    private static boolean explicit(Object value) {
        return value instanceof ItemInput || value instanceof FluidInput || value instanceof ItemOutput || value instanceof FluidOutput
                || value instanceof SizedIngredient || value instanceof SizedFluidIngredient || structured(value);
    }
    private static int integer(JsonObject json, String name, int fallback) {
        if (!json.has(name)) return fallback;
        double value = json.get(name).getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value <= 0 || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException(name + " must be a positive integer");
        return (int) value;
    }
    private static int positiveNumber(Object value, int fallback) {
        if (value == null) return fallback;
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number <= 0 || number > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Count/amount must be a positive integer");
        return (int) number;
    }
    private static float chance(JsonObject json) { return json.has("chance") ? json.get("chance").getAsFloat() : 1; }
    private static boolean perTick(JsonObject json) { return json.has("per_tick") && json.get("per_tick").getAsBoolean(); }
    private static float number(Object value, float fallback) { return value == null ? fallback : ((Number) value).floatValue(); }
    private static boolean bool(Object value) { return value != null && (Boolean) value; }
    private static JsonObject stripMetadata(JsonObject json) { json = json.deepCopy(); json.remove("chance"); json.remove("per_tick"); return json; }
    private CwiMachineRecipeComponents() {}
}
