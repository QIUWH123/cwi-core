package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.quiwh.cwi.machine.RecipeTransaction;
import net.quiwh.cwi.machine.MachineKinetics;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;
import com.simibubi.create.content.processing.recipe.HeatCondition;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class CreateMachineRecipeGameTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void vesselAdvancedMetadataSurvivesNativeCodec(GameTestHelper helper) {
        var codec = MachineRecipes.MIXING.serializer().codec().codec();
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = JsonParser.parseString("""
            {"ingredients":[{"ingredient":{"item":"minecraft:sand"},"catalyst":true},
              {"ingredient":{"type":"neoforge:single","fluid":"minecraft:water"},"amount":5,"per_tick":2,"tick_mode":"recipe"}],
             "results":[{"id":"minecraft:clay_ball"}],"priority":10,
             "output_pools":[{"entries":[{"stack":{"id":"minecraft:gravel","count":2},"weight":3}],"show_outputs":true}]}
            """);
        var recipe = codec.parse(ops, json).getOrThrow();
        var encoded = codec.encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
        helper.assertTrue(encoded.has("priority") && encoded.get("priority").getAsInt() == 10,
                "selection priority must survive native codec");
        helper.assertTrue(encoded.has("output_pools"), "independent byproduct pools must survive native codec");
        helper.assertTrue(encoded.toString().contains("catalyst") && encoded.toString().contains("tick_mode"),
                "input roles and recipe-clock intervals must survive native codec");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void furnaceMaximumHeatSurvivesCodecAndNetwork(GameTestHelper helper) {
        var serializer = MachineRecipes.BLAST_FURNACE.serializer();
        var registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var json = JsonParser.parseString("""
                {"ingredients":[{"item":"minecraft:iron_ingot"}],
                 "results":[{"id":"minecraft:lava","amount":100}],
                 "minimum_heat":1200,"maximum_heat":1600,"heat_requirement":"heated"}
                """);
        var recipe = serializer.codec().codec().parse(ops, json).getOrThrow();
        var encoded = serializer.codec().codec().encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
        helper.assertTrue(encoded.has("maximum_heat") && encoded.get("maximum_heat").getAsInt() == 1600,
                "furnace temperature cap survives native JSON decoding");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        try {
            serializer.streamCodec().encode(buffer, recipe);
            var copy = serializer.streamCodec().decode(buffer);
            var synced = serializer.codec().codec().encodeStart(ops, copy).getOrThrow().getAsJsonObject();
            helper.assertTrue(synced.get("maximum_heat").getAsInt() == 1600 && buffer.readableBytes() == 0,
                    "login synchronizes the maximum temperature");
        } finally { buffer.release(); }
        var changed = json.deepCopy().getAsJsonObject();
        changed.addProperty("maximum_heat", 1700);
        helper.assertTrue(!recipe.fingerprint(registries).equals(serializer.codec().codec().parse(ops, changed).getOrThrow().fingerprint(registries)),
                "changing the cap invalidates old processing state on reload");
        for (String fields : new String[]{"\"maximum_heat\":-1", "\"minimum_heat\":1601,\"maximum_heat\":1600",
                "\"heat_requirement\":\"heated\",\"maximum_heat\":1199", "\"heat_requirement\":\"superheated\",\"maximum_heat\":1899"}) {
            helper.assertTrue(serializer.codec().codec().parse(ops, JsonParser.parseString(
                    "{\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}]," + fields + "}")).isError(),
                    "impossible furnace temperature range rejected: " + fields);
        }
        for (var entry : List.of(MachineRecipes.INCUBATING, MachineRecipes.MIXING)) {
            helper.assertTrue(entry.serializer().codec().codec().parse(ops, JsonParser.parseString(
                    "{\"ingredients\":[{\"item\":\"minecraft:paper\"}],\"maximum_heat\":1600}")).isError(),
                    "unheated machines reject a temperature cap");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void createFormatUsesNativeIngredientsAndResults(GameTestHelper helper) {
        var codec = MachineRecipes.MIXING.serializer().codec().codec();
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var recipe = codec.parse(ops, JsonParser.parseString("""
                {"ingredients":[{"item":"minecraft:sand"},{"item":"minecraft:sand"},
                  {"type":"neoforge:single","fluid":"minecraft:water","amount":250}],
                 "results":[{"id":"minecraft:clay_ball","count":2,"chance":0.25},
                  {"id":"minecraft:lava","amount":50}],"processing_time":80}
                """)).getOrThrow();
        helper.assertTrue(recipe.getIngredients().size() == 2, "native view includes both sand units");
        helper.assertTrue(recipe.fluidInputs().getFirst().ingredient().amount() == 250, "native fluid quantity");
        helper.assertTrue(recipe.getRollableResults().getFirst().getChance() == .25f, "native item chance");
        helper.assertTrue(recipe.getFluidResults().getFirst().getAmount() == 50, "native fluid result");
        var encoded = codec.encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
        helper.assertTrue(encoded.has("ingredients") && encoded.has("results")
                && !encoded.has("item_inputs") && !encoded.has("fluid_outputs"), "one standard IO representation");
        var nativeCodec = com.simibubi.create.content.processing.recipe.ProcessingRecipeParams.CODEC.codec();
        var nativeParams = nativeCodec.parse(ops, encoded).getOrThrow();
        var nativeJson = nativeCodec.encodeStart(ops, nativeParams).getOrThrow().getAsJsonObject();
        helper.assertTrue(nativeJson.getAsJsonArray("ingredients").size() == 3
                && nativeJson.getAsJsonArray("results").asList().stream().map(v -> v.getAsJsonObject())
                        .anyMatch(v -> v.has("chance") && v.get("chance").getAsFloat() == .25f),
                "Create's own codec accepts the complete ordinary payload without losing amounts or chances");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void consumedContainersReturnWithoutLosingOutputs(GameTestHelper helper) {
        var recipe = new MachineRecipe(MachineRecipes.MIXING,
                List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.MILK_BUCKET), 1, 1, false)), List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.CLAY_BALL), 1, false)), List.of(),
                100, 0, 0, HeatCondition.NONE);
        var items = new ItemStackHandler(3);
        items.setStackInSlot(0, new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(RecipeTransaction.complete(recipe, items, null, 1, helper.getLevel().random), "recipe completes");
        int buckets = 0, clay = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            var stack = items.getStackInSlot(slot);
            if (stack.is(Items.BUCKET)) buckets += stack.getCount();
            if (stack.is(Items.CLAY_BALL)) clay += stack.getCount();
        }
        helper.assertTrue(buckets == 1 && clay == 1, "one returned bucket and one product");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void returnedContainersParticipateInAtomicCapacity(GameTestHelper helper) {
        var recipe = new MachineRecipe(MachineRecipes.MIXING,
                List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.MILK_BUCKET), 1, 1, false)), List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.CLAY_BALL), .5f, false)), List.of(),
                100, 0, 0, HeatCondition.NONE);
        var items = new ItemStackHandler(2);
        items.setStackInSlot(0, new ItemStack(Items.MILK_BUCKET));
        items.setStackInSlot(1, new ItemStack(Items.DIAMOND, 64));
        var random = net.minecraft.util.RandomSource.create(42);
        var untouched = net.minecraft.util.RandomSource.create(42);
        helper.assertTrue(!RecipeTransaction.finishTick(recipe, items, null, 1, random), "returned bucket and possible clay need two free product slots");
        helper.assertTrue(items.getStackInSlot(0).is(Items.MILK_BUCKET) && items.getStackInSlot(1).getCount() == 64,
                "failed capacity preserves inputs and existing outputs");
        helper.assertTrue(random.nextLong() == untouched.nextLong(), "blocked transaction does not advance chance RNG");
        items.setStackInSlot(1, ItemStack.EMPTY);
        helper.assertTrue(RecipeTransaction.finishTick(recipe, items, null, 1, random), "automation freeing space allows completion");
        helper.assertTrue(java.util.stream.IntStream.range(0, 2).anyMatch(i -> items.getStackInSlot(i).is(Items.BUCKET)), "returned bucket survives chance roll");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void vesselUsesCreateMixerActiveProcessingTime(GameTestHelper helper) {
        helper.assertTrue(MachineKinetics.mixerDuration(100, 256) == 16, "Create mixer: 100 time at 256RPM is 16 active ticks");
        helper.assertTrue(MachineKinetics.mixerDuration(100, 128) == 31, "Create mixer: 100 time at 128RPM is 31 active ticks");
        helper.assertTrue(MachineKinetics.mixerDuration(200, -64) == 91, "Create mixer: 200 time at either 64RPM is 91 active ticks");
        helper.succeed();
    }
}
