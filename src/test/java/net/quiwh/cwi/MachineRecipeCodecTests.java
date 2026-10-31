package net.quiwh.cwi;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

/** Runs against the native registries and actual NeoForge codecs, not substitutes. */
@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class MachineRecipeCodecTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void explicitRecipeRoundTrip(GameTestHelper helper) {
        verifyRoundTrip(helper.getLevel().registryAccess());
        helper.succeed();
    }

    public static void verifyRoundTrip(RegistryAccess registries) {
        for (var entry : MachineRecipes.entries()) {
            String json = entry == MachineRecipes.MIXING ? """
                    {"item_inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":3,"chance":0.25,"per_tick":true}],
                     "fluid_inputs":[{"ingredient":{"fluid":"minecraft:water"},"amount":250,"chance":0,"per_tick":false}],
                     "item_outputs":[{"stack":{"id":"minecraft:gold_ingot","count":2},"chance":0,"per_tick":false}],
                     "fluid_outputs":[{"stack":{"id":"minecraft:lava","amount":500},"chance":0.75,"per_tick":true}],
                     "processing_time":123,"minimum_rpm":64}
                    """ : entry == MachineRecipes.BLAST_FURNACE ? """
                    {"item_inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":3,"chance":0.25,"per_tick":true}],
                     "fluid_inputs":[{"ingredient":{"fluid":"minecraft:water"},"amount":250,"chance":0}],
                     "fluid_outputs":[{"stack":{"id":"minecraft:lava","amount":500},"chance":0.75,"per_tick":true}],
                     "processing_time":123,"minimum_heat":1200,"heat_requirement":"heated"}
                    """ : """
                    {"item_inputs":[{"ingredient":{"item":"minecraft:paper"},"chance":0.25}],
                     "item_outputs":[{"stack":{"id":"minecraft:map"},"chance":0}],
                     "processing_time":123,"minimum_rpm":64}
                    """;
            var recipe = decode(entry, registries, json);
            check(recipe.itemInputs().getFirst().count() == (entry == MachineRecipes.INCUBATING ? 1 : 3), "count lost");
            if (entry != MachineRecipes.INCUBATING) {
                check(recipe.itemInputs().getFirst().perTick(), "per-tick lost");
                check(recipe.fluidInputs().getFirst().chance() == 0, "zero input chance lost");
            }
            if (entry != MachineRecipes.BLAST_FURNACE) check(recipe.itemOutputs().getFirst().chance() == 0, "zero output chance lost");
            check(recipe.durationAtRpm(128) == 246, "RPM duration scaling");
            check(recipe.durationAtRpm(-128) == 246, "negative RPM uses magnitude");
            check(recipe.durationAtRpm(0) == Integer.MAX_VALUE, "zero RPM cannot finish");
            check(recipe.durationAtRpm(Float.NaN) == Integer.MAX_VALUE, "NaN RPM cannot finish");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                entry.serializer().streamCodec().encode(buffer, recipe);
                var copy = entry.serializer().streamCodec().decode(buffer);
                check(copy.fingerprint(registries).equals(recipe.fingerprint(registries)), "network lost recipe data");
                check(buffer.readableBytes() == 0, "trailing network data");
            } finally { buffer.release(); }
        }
        var recipe = decode(MachineRecipes.MIXING, registries, """
                {"item_inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":2}],
                 "item_outputs":[{"stack":{"id":"minecraft:gold_ingot"},"chance":0.25}],"processing_time":40}
                """);
        var same = decode(MachineRecipes.MIXING, registries, """
                {"processing_time":40,"item_outputs":[{"chance":0.25,"stack":{"id":"minecraft:gold_ingot"}}],
                 "item_inputs":[{"count":2,"ingredient":{"item":"minecraft:iron_ingot"}}]}
                """);
        check(recipe.fingerprint(registries).equals(same.fingerprint(registries)), "JSON key order changes progress identity");
        var changed = decode(MachineRecipes.MIXING, registries, """
                {"item_inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":3}],
                 "item_outputs":[{"stack":{"id":"minecraft:gold_ingot"},"chance":0.25}],"processing_time":40}
                """);
        check(!recipe.fingerprint(registries).equals(changed.fingerprint(registries)), "recipe mutation does not reset progress identity");
        var exposed = recipe.itemOutputs().getFirst().stack(); exposed.setCount(99);
        check(recipe.itemOutputs().getFirst().stack().getCount() == 1, "output accessor mutates native recipe");
        var overlap = new MachineRecipe(MachineRecipes.MIXING,
                java.util.List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1, 1, false),
                        new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 0, false)),
                java.util.List.of(new MachineRecipe.FluidInput(new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 100), 1, false),
                        new MachineRecipe.FluidInput(SizedFluidIngredient.of(Fluids.WATER, 100), 0, false)),
                java.util.List.of(), java.util.List.of(), 20, 0, 0, com.simibubi.create.content.processing.recipe.HeatCondition.NONE);
        check(overlap.matches(new MachineRecipe.Input(java.util.List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)),
                java.util.List.of(new FluidStack(Fluids.WATER, 100), new FluidStack(Fluids.LAVA, 100))), null), "overlapping ingredients/fluid predicates need reassignment");
        check(!overlap.matches(new MachineRecipe.Input(java.util.List.of(new ItemStack(Items.GOLD_INGOT, 2)),
                java.util.List.of(new FluidStack(Fluids.WATER, 100), new FluidStack(Fluids.LAVA, 100))), null), "zero-consumption catalyst absent");
        check(!overlap.matches(new MachineRecipe.Input(java.util.List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)),
                java.util.List.of(new FluidStack(Fluids.WATER, 99), new FluidStack(Fluids.LAVA, 100))), null), "missing one mB catalyst fluid");
        var displayRecipe = decode(MachineRecipes.INCUBATING, registries, """
                {"item_inputs":[{"ingredient":{"item":"minecraft:paper"}}],
                 "item_outputs":[{"stack":{"id":"minecraft:map"}}],
                 "incubation_display":{"microbe":{"id":"minecraft:slime_ball"},"yield_count":3,
                   "steps":[{"type":"deploying","item":{"id":"minecraft:sugar"},"count":2},
                            {"type":"filling","fluid":{"id":"minecraft:water","amount":250},"count":2}]}}
                """);
        var displayJson = MachineRecipes.INCUBATING.serializer().codec().codec().encodeStart(
                registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), displayRecipe).getOrThrow();
        check(displayJson.getAsJsonObject().has("incubation_display"), "incubator preparation display lost by native codec");
        var display = displayRecipe.incubationDisplay().orElseThrow();
        check(display.yieldCount() == 3 && display.microbe().is(Items.SLIME_BALL), "microbe/yield metadata changed");
        check(display.steps().getFirst().displayItem().is(Items.SUGAR) && display.steps().getFirst().displayItem().getCount() == 2,
                "preparation item total incorrect");
        check(display.steps().get(1).displayFluid().is(Fluids.WATER) && display.steps().get(1).displayFluid().getAmount() == 500,
                "preparation fluid total incorrect");
        display.microbe().setCount(99); display.steps().getFirst().item().setCount(99); display.steps().get(1).fluid().setAmount(99);
        check(display.microbe().getCount() == 1 && display.steps().getFirst().displayItem().getCount() == 2
                && display.steps().get(1).displayFluid().getAmount() == 500, "display accessors mutate recipe metadata");
        check(displayRecipe.itemInputs().size() == 1 && displayRecipe.fluidInputs().isEmpty(), "preparation metadata added physical inputs");
        var displayBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            var serializer = MachineRecipes.INCUBATING.serializer();
            serializer.streamCodec().encode(displayBuffer, displayRecipe);
            var copy = serializer.streamCodec().decode(displayBuffer);
            check(copy.incubationDisplay().orElseThrow().steps().get(1).displayFluid().getAmount() == 500, "login lost preparation metadata");
            check(copy.fingerprint(registries).equals(displayRecipe.fingerprint(registries)), "login display fingerprint mismatch");
            check(displayBuffer.readableBytes() == 0, "trailing display network data");
        } finally { displayBuffer.release(); }
        check(recipe.incubationDisplay().isEmpty(), "ordinary recipes unexpectedly contain display metadata");
    }

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void malformedRecipesRejected(GameTestHelper helper) {
        verifyMalformed(helper.getLevel().registryAccess());
        helper.succeed();
    }

    public static void verifyMalformed(RegistryAccess registries) {
        var entry = MachineRecipes.MIXING;
        var base = "{\"item_inputs\":[{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"count\":1}],\"processing_time\":20}";
        for (var changed : new String[]{base.replace("\"count\":1", "\"count\":0"),
                base.replace("\"count\":1", "\"count\":-1"),
                base.replace("\"count\":1", "\"chance\":-0.1"),
                base.replace("\"count\":1", "\"chance\":1.1"),
                base.replace("\"processing_time\":20", "\"processing_time\":0"),
                base.replace("\"processing_time\":20", "\"minimum_rpm\":-1"),
                base.replace("\"processing_time\":20", "\"minimum_heat\":-1"), "{\"processing_time\":20}"}) {
            check(entry.serializer().codec().codec().parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                    JsonParser.parseString(changed)).isError(), "malformed recipe accepted: " + changed);
        }
        for (var type : MachineRecipes.entries()) {
            String plate = "{\"item_inputs\":[{\"ingredient\":{\"item\":\"minecraft:paper\"}}]";
            if (type == MachineRecipes.BLAST_FURNACE) {
                reject(type, registries, plate + ",\"minimum_rpm\":1}");
                reject(type, registries, plate + ",\"item_outputs\":[{\"stack\":{\"id\":\"minecraft:map\"}}]}");
            } else {
                reject(type, registries, plate + ",\"minimum_heat\":1}");
                reject(type, registries, plate + ",\"heat_requirement\":\"heated\"}");
            }
        }
        for (String extra : new String[]{",\"fluid_inputs\":[{\"ingredient\":{\"fluid\":\"minecraft:water\"},\"amount\":1}]",
                ",\"fluid_outputs\":[{\"stack\":{\"id\":\"minecraft:water\",\"amount\":1}}]"})
            reject(MachineRecipes.INCUBATING, registries, "{\"item_inputs\":[{\"ingredient\":{\"item\":\"minecraft:paper\"}}]" + extra + "}");
        reject(MachineRecipes.INCUBATING, registries, base.replace("\"count\":1", "\"count\":2"));
        reject(MachineRecipes.INCUBATING, registries, base.replace("\"count\":1", "\"per_tick\":true"));
        String plate = "{\"item_inputs\":[{\"ingredient\":{\"item\":\"minecraft:paper\"}}],\"incubation_display\":";
        String display = "{\"microbe\":{\"id\":\"minecraft:slime_ball\"},\"yield_count\":3,\"steps\":[{\"type\":\"filling\",\"fluid\":{\"id\":\"minecraft:water\",\"amount\":250},\"count\":2}]}";
        reject(MachineRecipes.MIXING, registries, plate + display + "}");
        reject(MachineRecipes.BLAST_FURNACE, registries, plate + display + "}");
        for (String malformed : new String[]{display.replace("\"yield_count\":3", "\"yield_count\":0"),
                display.replace("\"count\":2", "\"count\":0"), display.replace("\"count\":2", "\"count\":2147483647"),
                display.replace("\"type\":\"filling\"", "\"type\":\"deploying\""),
                display.replace("\"type\":\"filling\"", "\"type\":\"unknown\""),
                display.replace("\"fluid\":", "\"item\":{\"id\":\"minecraft:sugar\"},\"fluid\":"),
                display.replace("\"amount\":250", "\"amount\":0")})
            reject(MachineRecipes.INCUBATING, registries, plate + malformed + "}");
    }

    private static void reject(MachineRecipes.Entry entry, RegistryAccess registries, String json) {
        check(entry.serializer().codec().codec().parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                JsonParser.parseString(json)).isError(), "physically unsupported recipe accepted: " + entry.getId() + " " + json);
    }

    private static MachineRecipe decode(MachineRecipes.Entry entry, RegistryAccess registries, String json) {
        return entry.serializer().codec().codec().parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                JsonParser.parseString(json)).getOrThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
