package net.quiwh.cwi;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;
import net.quiwh.cwi.hammer.HammerRecipe;
import net.quiwh.cwi.hammer.HammerRecipes;

/** Exercises the staged illustrative script through the real ServerEvents lifecycle and a server data reload. */
@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class MachineScriptLifecycleGameTests {
    @GameTest(template = "empty", templateNamespace = "cwi", batch = "cwi_script_lifecycle", timeoutTicks = 400)
    public static void examplesLoadAndReload(GameTestHelper helper) {
        verifyRollingExample(helper.getLevel());
        EquipmentUpgradeGameTests.verifyScripts(helper.getLevel());
        var schemaBefore = OptionalSchemaGameTests.verify(helper.getLevel());
        var upgradeBefore = EquipmentUpgradeGameTests.scriptFingerprint(helper.getLevel());
        var upgradeInstance = EquipmentUpgradeGameTests.script(helper.getLevel(), "example/feather_falling_manual_only");
        var server = helper.getLevel().getServer();
        List<RecipeHolder<MachineRecipe>> before = verifyExamples(helper.getLevel());
        var contractsBefore = verifyMachineContracts(helper.getLevel());
        List<RecipeHolder<HammerRecipe>> hammerBefore = verifyHammerExamples(helper.getLevel());
        var callableBefore = callableFingerprints(helper.getLevel());
        var interactionsBefore = WorldInteractionGameTests.verifyScripts(helper.getLevel());
        var interactionInstance = net.quiwh.cwi.interaction.InteractionRecipes.rules(helper.getLevel(),
                net.quiwh.cwi.interaction.InteractionRecipes.Kind.ITEM_IMPACT).getFirst().value();
        var scriptManager = dev.latvian.mods.kubejs.KubeJS.getStartupScriptManager();
        var scriptContext = (dev.latvian.mods.kubejs.script.KubeJSContext)scriptManager.contextFactory.enter();
        scriptContext.evaluateString(scriptContext.topLevelScope, "global.cwiCallableReloadTag = true", "callable-tag-reload", 1, null);
        var fingerprints = before.stream().map(holder -> holder.value().fingerprint(helper.getLevel().registryAccess())).toList();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
            try {
                if (failure != null) throw new AssertionError("Server data reload failed", failure);
                verifyRollingExample(helper.getLevel());
                check(interactionsBefore.equals(WorldInteractionGameTests.verifyScripts(helper.getLevel())), "world interaction rules changed across reload");
                check(interactionInstance != net.quiwh.cwi.interaction.InteractionRecipes.rules(helper.getLevel(),
                        net.quiwh.cwi.interaction.InteractionRecipes.Kind.ITEM_IMPACT).getFirst().value(),
                        "reload must replace cached world interaction rules");
                check(callableBefore.equals(callableFingerprints(helper.getLevel())), "callable native payloads changed after reload");
                var tags = (MachineRecipe)helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:callable/tags")).orElseThrow().value();
                check(tags.itemInputs().getFirst().ingredient().test(new ItemStack(Items.END_ROD))
                        && !tags.itemInputs().getFirst().ingredient().test(new ItemStack(Items.LIGHTNING_ROD)), "startup item descriptor must rebind symbolic tags after reload");
                check(tags.fluidInputs().getFirst().ingredient().test(new FluidStack(Fluids.LAVA, 1))
                        && !tags.fluidInputs().getFirst().ingredient().test(new FluidStack(Fluids.WATER, 1)), "startup fluid descriptor must rebind symbolic tags after reload");
                EquipmentUpgradeGameTests.verifyScripts(helper.getLevel());
                check(upgradeInstance != EquipmentUpgradeGameTests.script(helper.getLevel(), "example/feather_falling_manual_only"), "reload must reconstruct equipment recipe");
                check(upgradeBefore.equals(EquipmentUpgradeGameTests.scriptFingerprint(helper.getLevel())), "upgrade definitions changed or duplicated after reload");
                check(schemaBefore.equals(OptionalSchemaGameTests.verify(helper.getLevel())), "Test.js changed after reload");
                var after = verifyExamples(helper.getLevel());
                var contractsAfter = verifyMachineContracts(helper.getLevel());
                for (int index = 0; index < contractsBefore.size(); index++) {
                    check(contractsBefore.get(index).value() != contractsAfter.get(index).value(), "reload did not reconstruct shorthand recipe");
                    check(contractsBefore.get(index).value().fingerprint(helper.getLevel().registryAccess()).equals(
                            contractsAfter.get(index).value().fingerprint(helper.getLevel().registryAccess())),
                            "shorthand recipe changed after reload");
                }
                var hammerAfter = verifyHammerExamples(helper.getLevel());
                for (int index = 0; index < before.size(); index++) {
                    check(before.get(index).value() != after.get(index).value(), "reload did not reconstruct script recipe");
                    check(fingerprints.get(index).equals(after.get(index).value().fingerprint(helper.getLevel().registryAccess())),
                            "script recipe changed after reload");
                }
                for (int index = 0; index < hammerBefore.size(); index++) {
                    check(hammerBefore.get(index).value() != hammerAfter.get(index).value(), "reload did not reconstruct hammer recipe");
                    check(hammerBefore.get(index).value().stage() == hammerAfter.get(index).value().stage(),
                            "hammer recipe stage changed after reload");
                }
                helper.succeed();
            } catch (Throwable error) { helper.fail(error.toString()); }
        }));
    }

    private static java.util.Map<String, String> callableFingerprints(Level level) {
        var ops = level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var map = new java.util.TreeMap<String, String>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            if (holder.id().getNamespace().equals("cwi") && holder.id().getPath().startsWith("callable/"))
                map.put(holder.id().toString(), net.minecraft.world.item.crafting.Recipe.CODEC.encodeStart(ops, holder.value()).getOrThrow().toString());
        }
        check(map.size() >= 10, "callable matrix recipes missing");
        return map;
    }

    private static void verifyRollingExample(Level level) {
        if (!net.neoforged.fml.ModList.get().isLoaded("createaddition")
                || !net.neoforged.fml.ModList.get().isLoaded("kubejs_create")) return;
        var holder = level.getRecipeManager().byKey(ResourceLocation.parse("cwi:example/rolling_assembly")).orElseThrow();
        check(holder.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe,
                "KubeJS rolling assembly survives initial load and reload");
    }

    private static List<RecipeHolder<MachineRecipe>> verifyExamples(Level level) {
        var furnace = find(level, MachineRecipes.BLAST_FURNACE, "blast_furnace");
        var incubator = find(level, MachineRecipes.INCUBATING, "incubating");
        var mixer = find(level, MachineRecipes.MIXING, "mixing_vessel");
        var hot = furnace.value();
        check(hot.itemInputs().size() == 1 && hot.itemInputs().getFirst().ingredient().test(new ItemStack(Items.COBBLESTONE))
                && hot.itemInputs().getFirst().count() == 1, "furnace script input");
        check(hot.itemOutputs().isEmpty() && hot.fluidOutputs().size() == 1 && hot.fluidOutputs().getFirst().stack().is(Fluids.LAVA)
                && hot.fluidOutputs().getFirst().stack().getAmount() == 250 && hot.fluidOutputs().getFirst().chance() == 1,
                "furnace script counted fluid output");
        check(hot.getProcessingDuration() == 200 && hot.getRequiredHeat() == HeatCondition.HEATED, "furnace script time/heat DSL");
        var culture = incubator.value();
        check(culture.itemInputs().size() == 1 && culture.itemInputs().getFirst().ingredient().test(new ItemStack(Items.GLASS_BOTTLE)),
                "incubator script input");
        check(culture.itemOutputs().size() == 1 && culture.itemOutputs().getFirst().stack().is(Items.HONEY_BOTTLE)
                && culture.itemOutputs().getFirst().chance() == .5f && culture.getProcessingDuration() == 400 && culture.minimumRpm() == 64,
                "incubator script output/chance/time/RPM DSL");
        var display = culture.incubationDisplay().orElseThrow(() -> new AssertionError("incubator preparation display absent"));
        check(display.microbe().is(Items.SLIME_BALL) && display.yieldCount() == 3 && display.steps().size() == 2,
                "incubator script microbe/yield/preparation metadata");
        var deploying = display.steps().get(0);
        check(deploying.type().name().equals("DEPLOYING") && deploying.item().is(Items.SUGAR)
                && deploying.count() == 2 && deploying.displayItem().getCount() == 2,
                "incubator deployment display uses native KubeJS string stack");
        var filling = display.steps().get(1);
        check(filling.type().name().equals("FILLING") && filling.fluid().is(Fluids.WATER)
                && filling.fluid().getAmount() == 250 && filling.count() == 2 && filling.displayFluid().getAmount() == 500,
                "incubator filling display preserves counted native KubeJS fluid string");
        check(new ItemStack(Items.GLASS_BOTTLE).is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cwi", "petri_dish"))),
                "example tag event did not permit bottle incubation");
        var mix = mixer.value();
        check(mix.itemInputs().size() == 1 && mix.itemInputs().getFirst().ingredient().test(new ItemStack(Items.SAND))
                && mix.itemInputs().getFirst().count() == 2, "mixer script tag/counted input");
        check(mix.fluidInputs().size() == 1 && mix.fluidInputs().getFirst().ingredient().test(new FluidStack(Fluids.WATER, 250))
                && mix.fluidInputs().getFirst().ingredient().amount() == 250, "mixer script fluid input");
        check(mix.itemOutputs().size() == 1 && mix.itemOutputs().getFirst().stack().is(Items.CLAY_BALL)
                && mix.getProcessingDuration() == 100 && mix.minimumRpm() == 16 && mix.durationAtRpm(128) == 200,
                "mixer script output/time/RPM DSL");
        return List.of(furnace, incubator, mixer);
    }

    private static List<RecipeHolder<HammerRecipe>> verifyHammerExamples(Level level) {
        var blockOne = findHammer(level, HammerRecipes.BLOCK_CONVERSION, "hammer_block_stage_1");
        var blockTwo = findHammer(level, HammerRecipes.BLOCK_CONVERSION, "hammer_block_stage_2");
        var depot = findHammer(level, HammerRecipes.DEPOT_CONVERSION, "hammer_depot_conversion");
        var stageOne = HammerRecipes.findBlock(level, new ItemStack(Items.SMOOTH_BASALT), 1).orElseThrow();
        check(stageOne.id().equals(blockOne.id()), "stage one hammer picks the applicable stage one conversion");
        var stageTwo = HammerRecipes.findBlock(level, new ItemStack(Items.SMOOTH_BASALT), 3).orElseThrow();
        check(stageTwo.id().equals(blockTwo.id()), "hammer lookup selects highest applicable stage");
        check(HammerRecipes.findBlock(level, new ItemStack(Items.SMOOTH_BASALT), 0).isEmpty(),
                "zero-stage hammer cannot select a conversion");
        check(blockTwo.value().target().is(Items.STONE) && blockTwo.value().drops().size() == 1
                && blockTwo.value().drops().getFirst().stack().getCount() == 4
                && blockTwo.value().drops().getFirst().chance() == .75f, "block conversion target and stochastic drop loaded");
        check(depot.value().input().test(new ItemStack(Items.RAW_IRON))
                && depot.value().target().getItem() == BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:crushed_raw_iron"))
                && depot.value().stage() == 3, "depot conversion input, output, and stage loaded");
        check(HammerRecipes.findDepot(level, new ItemStack(Items.RAW_IRON), 2).isEmpty(),
                "depot conversion respects required hammer stage");
        return List.of(blockOne, blockTwo, depot);
    }

    private static List<RecipeHolder<MachineRecipe>> verifyMachineContracts(Level level) {
        var furnace = contract(level, MachineRecipes.BLAST_FURNACE, "furnace");
        var hot = furnace.value();
        check(hot.itemInputs().size() == 2 && hot.itemInputs().get(0).count() == 2
                && hot.itemInputs().get(0).ingredient().test(new ItemStack(Items.SAND))
                && hot.itemInputs().get(1).count() == 3
                && hot.itemInputs().get(1).ingredient().test(new ItemStack(Items.COBBLESTONE)),
                "standard furnace counted tag and Item.of inputs");
        check(hot.fluidInputs().size() == 1 && hot.fluidInputs().getFirst().ingredient().amount() == 125
                && hot.fluidInputs().getFirst().ingredient().test(new FluidStack(Fluids.WATER, 125))
                && hot.fluidOutputs().getFirst().stack().getAmount() == 375
                && hot.fluidOutputs().getFirst().stack().is(Fluids.LAVA), "furnace mixed inputs and Fluid.of output");
        var incubator = contract(level, MachineRecipes.INCUBATING, "incubator");
        check(incubator.value().itemInputs().getFirst().ingredient().test(new ItemStack(Items.GLASS_BOTTLE))
                && incubator.value().itemOutputs().getFirst().stack().is(Items.HONEY_BOTTLE)
                && incubator.value().itemOutputs().getFirst().stack().getCount() == 2, "incubator scalar input/output");
        var mixer = contract(level, MachineRecipes.MIXING, "mixed");
        var mix = mixer.value();
        check(mix.itemInputs().size() == 1 && mix.itemInputs().getFirst().count() == 2
                && mix.fluidInputs().getFirst().ingredient().amount() == 250
                && mix.itemOutputs().getFirst().stack().getCount() == 3
                && mix.fluidOutputs().getFirst().stack().getAmount() == 75, "mixer splits mixed item/fluid arrays");
        var reset = contract(level, MachineRecipes.MIXING, "reset");
        check(reset.value().fluidInputs().isEmpty() && reset.value().fluidOutputs().isEmpty()
                && reset.value().itemInputs().getFirst().count() == 4
                && reset.value().itemInputs().getFirst().ingredient().test(new ItemStack(Items.DIRT))
                && reset.value().itemOutputs().getFirst().stack().is(Items.GRAVEL)
                && reset.value().itemOutputs().getFirst().stack().getCount() == 2, "inputs/outputs replace all channels without stale constructor data");
        var aliases = contract(level, MachineRecipes.BLAST_FURNACE, "aliases");
        check(aliases.value().itemInputs().size() == 1 && aliases.value().fluidOutputs().getFirst().stack().getAmount() == 50,
                "singular input/output fluent aliases");
        var advanced = contract(level, MachineRecipes.MIXING, "advanced");
        check(advanced.value().itemInputs().getFirst().count() == 3
                && advanced.value().itemOutputs().getFirst().stack().getCount() == 2
                && advanced.value().itemOutputs().getFirst().chance() == .25f
                && advanced.value().fluidInputs().getFirst().chance() == .5f
                && advanced.value().fluidInputs().getFirst().perTick()
                && advanced.value().fluidOutputs().getFirst().chance() == .75f, "explicit IO methods preserve advanced metadata and accept counted strings");
        var replaced = contract(level, MachineRecipes.MIXING, "replace");
        check(replaced.value().itemInputs().getFirst().ingredient().test(new ItemStack(Items.RED_SAND))
                && replaced.value().itemInputs().getFirst().count() == 2
                && replaced.value().itemInputs().getFirst().chance() == .5f && replaced.value().itemInputs().getFirst().perTick()
                && replaced.value().itemOutputs().getFirst().stack().is(Items.BRICK)
                && replaced.value().itemOutputs().getFirst().stack().getCount() == 3
                && replaced.value().itemOutputs().getFirst().chance() == .25f && replaced.value().itemOutputs().getFirst().perTick(),
                "standard KubeJS replacements preserve counts and processing metadata: "
                        + MachineRecipes.MIXING.serializer().codec().codec().encodeStart(
                        level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), replaced.value()).getOrThrow());
        var chance = contract(level, MachineRecipes.MIXING, "chance");
        check(chance.value().itemOutputs().getFirst().stack().is(Items.BRICK)
                && chance.value().itemOutputs().getFirst().stack().getCount() == 2
                && chance.value().itemOutputs().getFirst().chance() == .5f
                && chance.value().itemInputs().getFirst().count() == 2
                && chance.value().itemInputs().getFirst().chance() == .5f
                && chance.value().itemInputs().getFirst().perTick()
                && chance.value().fluidInputs().getFirst().ingredient().amount() == 100,
                "Create chance output and explicit fluid setter compose with standard constructor");
        return List.of(furnace, incubator, mixer, reset, aliases, advanced, replaced, chance);
    }

    private static RecipeHolder<MachineRecipe> contract(Level level, MachineRecipes.Entry type, String path) {
        var id = ResourceLocation.fromNamespaceAndPath("cwi", "contracts/machine_" + path);
        return level.getRecipeManager().getAllRecipesFor(type.recipeType()).stream()
                .filter(holder -> holder.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("standard KubeJS machine recipe absent: " + id));
    }

    private static RecipeHolder<MachineRecipe> find(Level level, MachineRecipes.Entry type, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cwi", "example/" + path);
        var found = level.getRecipeManager().getAllRecipesFor(type.recipeType()).stream().filter(holder -> holder.id().equals(id)).toList();
        check(found.size() == 1, "staged ServerEvents recipe absent/duplicated: " + id);
        return found.getFirst();
    }
    private static RecipeHolder<HammerRecipe> findHammer(Level level, HammerRecipes.Entry type, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cwi", "example/" + path);
        var found = level.getRecipeManager().getAllRecipesFor(type.recipeType()).stream().filter(holder -> holder.id().equals(id)).toList();
        check(found.size() == 1, "staged ServerEvents hammer recipe absent/duplicated: " + id);
        return found.getFirst();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
