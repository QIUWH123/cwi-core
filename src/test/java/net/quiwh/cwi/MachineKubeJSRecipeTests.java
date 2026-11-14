package net.quiwh.cwi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.latvian.mods.kubejs.codec.KubeJSCodecs;
import dev.latvian.mods.kubejs.plugin.builtin.BuiltinKubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.RecipeTypeRegistryContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.schema.JsonRecipeSchemaLoader;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaData;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.recipe.schema.postprocessing.RecipePostProcessorType;
import dev.latvian.mods.kubejs.util.ErrorStack;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.kubejs.util.RegistryOpsContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.compat.CwiKubeJSPlugin;
import net.quiwh.cwi.compat.kubejs.CwiHammerRecipeComponents;
import net.quiwh.cwi.compat.kubejs.CwiMachineRecipeComponents;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class MachineKubeJSRecipeTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void machineSchemas(GameTestHelper helper) throws Exception {
        verify(helper.getLevel().registryAccess()); helper.succeed();
    }

    public static void verify(RegistryAccess access) throws Exception {
        var registries = new RegistryAccessContainer(access.freeze());
        var cx = new ContextFactory().enter();
        RecipeScriptContext context = new RecipeScriptContext() {
            @Override public Context cx() { return cx; }
            @Override public RegistryAccessContainer registries() { return registries; }
            @Override public RegistryOpsContainer ops() { return registries; }
            @Override public KubeRecipe recipe() { return null; }
            @Override public ErrorStack errors() { return ErrorStack.NONE; }
        };
        var itemInput = CwiMachineRecipeComponents.ITEM_INPUT.instance().wrap(context,
                Map.of("ingredient", "minecraft:iron_ingot", "count", 3, "chance", 0, "per_tick", true));
        check(itemInput.count() == 3 && itemInput.chance() == 0 && itemInput.perTick(), "KJS item input metadata");
        check(itemInput.ingredient().test(new ItemStack(Items.IRON_INGOT)), "KJS item ingredient");
        var fluidInput = CwiMachineRecipeComponents.FLUID_INPUT.instance().wrap(context,
                Map.of("ingredient", "minecraft:water", "amount", 250, "chance", 0.5));
        check(fluidInput.ingredient().amount() == 250 && fluidInput.chance() == 0.5f, "KJS fluid input metadata");
        check(fluidInput.ingredient().test(new FluidStack(Fluids.WATER, 250)), "KJS fluid ingredient");
        var output = CwiMachineRecipeComponents.ITEM_OUTPUT.instance().wrap(context,
                Map.of("stack", new ItemStack(Items.GOLD_INGOT, 2), "chance", 0, "per_tick", true));
        check(output.stack().getCount() == 2 && output.chance() == 0 && output.perTick(), "KJS zero chance item output");
        var fluidOutput = CwiMachineRecipeComponents.FLUID_OUTPUT.instance().wrap(context,
                Map.of("stack", new FluidStack(Fluids.LAVA, 500), "chance", 0.75, "per_tick", true));
        check(fluidOutput.stack().getAmount() == 500 && fluidOutput.chance() == 0.75f && fluidOutput.perTick(), "KJS fluid output");
        var js = cx.evaluateString(cx.initStandardObjects(),
                "({ingredient:'minecraft:iron_ingot', count:2, chance:0.2, per_tick:true})", "native-machine-component", 1, null);
        var scripted = CwiMachineRecipeComponents.ITEM_INPUT.instance().wrap(context, js);
        check(scripted.count() == 2 && scripted.chance() == 0.2f && scripted.perTick(), "actual Rhino object wraps");
        var scriptedFluid = CwiMachineRecipeComponents.FLUID_OUTPUT.instance().wrap(context, cx.evaluateString(cx.initStandardObjects(),
                "({stack:{id:'minecraft:lava', amount:250}, chance:1})", "native-machine-example", 1, null));
        check(scriptedFluid.stack().is(Fluids.LAVA) && scriptedFluid.stack().getAmount() == 250, "illustrative Rhino fluid output wraps");
        var nestedFluid = CwiMachineRecipeComponents.FLUID_INPUT.instance().wrap(context, cx.evaluateString(cx.initStandardObjects(),
                "({ingredient:{fluid:'minecraft:water'}, amount:123})", "native-machine-ingredient", 1, null));
        check(nestedFluid.ingredient().amount() == 123 && nestedFluid.ingredient().test(new FluidStack(Fluids.WATER, 123)),
                "Rhino nested native fluid ingredient wraps");
        var display = CwiMachineRecipeComponents.INCUBATION_DISPLAY.instance().wrap(context, cx.evaluateString(cx.initStandardObjects(), """
                ({microbe:{id:'minecraft:slime_ball'}, yield_count:3, steps:[
                  {type:'deploying',item:{id:'minecraft:sugar'},count:2},
                  {type:'filling',fluid:'minecraft:water',amount:250,count:2}]})
                """, "native-incubation-display", 1, null));
        check(display.microbe().is(Items.SLIME_BALL) && display.yieldCount() == 3, "Rhino microbe and yield wrap");
        check(display.steps().getFirst().displayItem().getCount() == 2, "Rhino preparation item count wraps");
        check(display.steps().get(1).displayFluid().getAmount() == 500, "Rhino preparation fluid amount times count wraps");
        var hammerDrop = CwiHammerRecipeComponents.DROP.instance().wrap(context,
                Map.of("item", "minecraft:gravel", "count", 4, "chance", 0.75));
        check(hammerDrop.stack().is(Items.GRAVEL) && hammerDrop.stack().getCount() == 4 && hammerDrop.chance() == .75f,
                "KubeJS hammer drop preserves item/count/chance");
        var zeroChanceDrop = CwiHammerRecipeComponents.DROP.instance().wrap(context, cx.evaluateString(cx.initStandardObjects(),
                "({item:'minecraft:gravel', count:2, chance:0})", "native-hammer-drop", 1, null));
        check(zeroChanceDrop.stack().getCount() == 2 && zeroChanceDrop.chance() == 0,
                "KubeJS hammer drops support zero chance");
        var nestedDrop = CwiHammerRecipeComponents.DROP.instance().wrap(context,
                Map.of("stack", "minecraft:gravel", "count", 3, "chance", 0.25));
        check(nestedDrop.stack().getCount() == 3 && nestedDrop.chance() == .25f,
                "KubeJS hammer drops accept stack/count/chance form");

        var componentTypes = new HashMap<ResourceLocation, RecipeComponentType<?>>();
        new BuiltinKubeJSPlugin().registerRecipeComponents(type -> componentTypes.put(type.id(), type));
        new CwiKubeJSPlugin().registerRecipeComponents(type -> componentTypes.put(type.id(), type));
        var storage = new RecipeSchemaStorage(null);
        var registryContext = new RecipeTypeRegistryContext(registries, storage);
        Codec<RecipeComponent<?>> unit = KubeJSCodecs.KUBEJS_ID.comapFlatMap(id -> {
            var type = componentTypes.get(id);
            return type == null ? DataResult.error(() -> "Unknown component " + id) : DataResult.success(type.instance());
        }, component -> component.type().id());
        Codec<RecipeComponent<?>> object = KubeJSCodecs.KUBEJS_ID.dispatch("type", value -> value.type().id(),
                id -> componentTypes.get(id).mapCodec(registryContext));
        storage.recipeComponentCodec = Codec.either(unit, object).xmap(value -> value.map(left -> left, right -> right), Either::left);
        var postProcessors = new HashMap<ResourceLocation, RecipePostProcessorType<?>>();
        new BuiltinKubeJSPlugin().registerRecipePostProcessors(type -> postProcessors.put(type.id(), type));
        storage.recipePostProcessorCodec = KubeJSCodecs.KUBEJS_ID.dispatch("type", value -> value.type().id(),
                id -> postProcessors.get(id).mapCodec().apply(registryContext));
        var resources = new HashMap<ResourceLocation, Resource>();
        for (String name : new String[]{"blast_furnace_processing", "incubating", "mixing_vessel_mixing", "block_conversion", "depot_conversion"}) {
            String path = "/data/cwi/kubejs/recipe_schema/" + name + ".json";
            resources.put(ResourceLocation.fromNamespaceAndPath("cwi", "kubejs/recipe_schema/" + name + ".json"),
                    new Resource(null, () -> MachineKubeJSRecipeTests.class.getResourceAsStream(path)));
            try (var stream = MachineKubeJSRecipeTests.class.getResourceAsStream("/data/cwi/kubejs/recipe_schema/" + name + ".json")) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
                var schema = RecipeSchemaData.CODEC.apply(registryContext).parse(registries.json(), json).getOrThrow();
                var keys = schema.keys().orElseThrow();
                check(keys.size() == (name.equals("incubating") ? 5 : name.equals("block_conversion") ? 4
                        : name.equals("depot_conversion") ? 3 : name.equals("blast_furnace_processing") ? 8 : 6), "physical schema keys for " + name);
                var methods=keys.stream().flatMap(key->key.functionNames().stream()).toList();
                if (name.endsWith("conversion")) {
                    check(methods.contains("input") && methods.contains("target"), "DSL input/target methods for " + name);
                } else {
                    check(methods.contains("processingTime") && methods.contains("ingredients"), "DSL duration/input methods for " + name);
                }
                if (!name.endsWith("conversion")) {
                    verifyNumericDuration(keys.stream().filter(key -> key.name().equals("processing_time"))
                            .findFirst().orElseThrow().type(), context, name);
                    check(methods.contains("heatRequirement") == name.equals("blast_furnace_processing"), "heat is furnace-only");
                    check(methods.contains("maximumHeat") == name.equals("blast_furnace_processing"), "maximum temperature is furnace-only");
                    check(methods.contains("minimumRpm") != name.equals("blast_furnace_processing"), "RPM is kinetic-only");
                    check(methods.contains("results"), "canonical results setter");

                    check(methods.contains("incubationDisplay") == name.equals("incubating"), "incubator-only display metadata method");
                }
                check(methods.contains("requireStage") == name.endsWith("conversion"), "hammer conversion has a stage method");
                check(methods.contains("input") == name.endsWith("conversion"), "hammer conversion has an input method");
                check(methods.contains("target") == name.endsWith("conversion"), "hammer conversion has a target method");
                check(methods.contains("drops") == name.equals("block_conversion"), "only block conversion exposes drops");
                for (var key : keys) check(key.optional().isPresent() || key.defaultOptional(), "zero-argument fluent constructor defaults " + key.name());
            }
        }
        JsonRecipeSchemaLoader.load(registryContext, registries.json(), new RecipeSchemaRegistry(storage), new ResourceManager() {
            @Override public Set<String> getNamespaces() { return Set.of("cwi"); }
            @Override public Optional<Resource> getResource(ResourceLocation id) { return Optional.ofNullable(resources.get(id)); }
            @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
            @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
                return resources.entrySet().stream().filter(entry -> entry.getKey().getPath().startsWith(path) && filter.test(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
                return listResources(path, filter).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));
            }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
        });
        for (String name : new String[]{"blast_furnace_processing", "incubating", "mixing_vessel_mixing", "block_conversion", "depot_conversion"}) {
            var loaded = storage.namespace("cwi").getRegisteredOrThrow(name).schema;
            check(loaded.constructors().containsKey(0), "native loader supplies zero-argument fluent constructor for " + name);
            check(loaded.constructors().get(0).keys.isEmpty(), "zero-argument constructor has no required positional keys for " + name);
        }
    }

    static <T> void verifyNumericDuration(RecipeComponent<T> component, RecipeScriptContext context, String name) {
        var encoded = component.codec().encodeStart(context.ops().json(), component.wrap(context, 80)).getOrThrow();
        check(encoded.getAsInt() == 80, "numeric processing ticks retained for " + name);
        for (String duration : List.of("5s", "1m")) {
            boolean rejected = false;
            try {
                component.wrap(context, duration);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            check(rejected, "processing duration must reject time string " + duration + " for " + name);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
