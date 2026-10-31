package net.quiwh.cwi;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.recipe.MachineRecipe;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class CallableRecipeGameTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void descriptorsReachNativeMachineFields(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:callable/mixed"))
                .orElseThrow(() -> new AssertionError("Callable recipe factory did not create native recipe"));
        var recipe = (MachineRecipe) holder.value();
        check(recipe.itemInputs().getFirst().count() == 2 && recipe.itemInputs().getFirst().chance() == 0, "counted tag catalyst");
        check(recipe.fluidInputs().getFirst().ingredient().amount() == 250 && recipe.fluidInputs().getFirst().perTick(), "fluid timing and amount");
        check(recipe.itemOutputs().getFirst().stack().getCount() == 2 && recipe.itemOutputs().getFirst().chance() == .25F, "item chance");
        check(recipe.fluidOutputs().getFirst().stack().getAmount() == 75, "fluid output");
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void allCoreEntryPathsReachNativePayloads(GameTestHelper helper) {
        var fluent = machine(helper, "fluent");
        check(fluent.itemInputs().getFirst().count() == 2 && !fluent.itemInputs().getFirst().perTick(), "generated setter resets timing");
        check(fluent.itemInputs().getFirst().ingredient().test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP)), "startup descriptor reused by server");
        check(fluent.itemOutputs().getFirst().perTick() && fluent.itemOutputs().getFirst().chance() == .5F, "custom output alias");
        check(fluent.fluidOutputs().getFirst().stack().getAmount() == 50 && fluent.fluidOutputs().getFirst().chance() == 0, "recipe.set fluid output");
        check(machine(helper, "furnace").fluidOutputs().getFirst().chance() == .75F, "furnace native chance");
        check(machine(helper, "incubating").itemInputs().getFirst().chance() == 0 && machine(helper, "incubating").itemOutputs().getFirst().perTick(), "incubator catalyst and tick result");
        check(machine(helper, "snapshot").itemOutputs().getFirst().stack().getCount() == 3, "source stack mutation must not leak");
        var tags = machine(helper, "tags");
        check(tags.itemInputs().getFirst().ingredient().test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LIGHTNING_ROD)), "item tag identity");
        check(tags.fluidInputs().getFirst().ingredient().test(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1)), "same-name fluid tag routed by kind");
        var replaced = machine(helper, "replace_machine");
        check(replaced.itemInputs().getFirst().count() == 2 && replaced.itemInputs().getFirst().chance() == 1 && !replaced.itemInputs().getFirst().perTick(), "complete item input replacement");
        check(replaced.itemOutputs().getFirst().stack().getCount() == 1 && replaced.itemOutputs().getFirst().chance() == 1 && !replaced.itemOutputs().getFirst().perTick(), "same-ID item result replacement resets metadata");
        check(replaced.fluidOutputs().getFirst().stack().getAmount() == 50 && replaced.fluidOutputs().getFirst().chance() == 1 && !replaced.fluidOutputs().getFirst().perTick(), "complete fluid replacement");
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void createNativePayloadsKeepPositionsCountsAndWeights(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("kubejs_create")) { helper.succeed(); return; }
        var mixing = processing(helper, "create_mixing");
        check(mixing.getIngredients().size() == 2 && mixing.getFluidIngredients().getFirst().amount() == 100, "bounded ingredient spreading and fluid tag amount");
        check(mixing.getRollableResults().getFirst().getStack().getCount() == 2 && mixing.getRollableResults().getFirst().getChance() == .25F, "native Create item roll");
        check(mixing.getFluidResults().getFirst().getAmount() == 50, "deterministic fluid result");
        check(processing(helper, "create_filling").getFluidIngredients().getFirst().amount() == 25, "filling native ingredient");
        var deploying = processing(helper, "create_deploying");
        check(deploying.getIngredients().size() == 2 && deploying.getRollableResults().size() == 2, "deploying repeated positions are not merged");
        var pressing = processing(helper, "create_pressing");
        check(pressing.getRollableResults().getFirst().getChance() == .125F, "Create recipe.set");
        var assembly = (com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe)recipe(helper, "create_assembly");
        check(assembly.resultPool.size() == 2 && assembly.resultPool.get(0).getChance() == 2 && assembly.resultPool.get(1).getChance() == .25F, "ordered assembly weights");
        check(assembly.getLoops() == 2 && assembly.getSequence().size() == 3 && assembly.getTransitionalItem().getCount() == 1, "transitional and nested fields");
        var replacement = processing(helper, "replace_pressing").getRollableResults().getFirst();
        check(replacement.getStack().getCount() == 2 && replacement.getChance() == 1, "Create complete output replacement");
        var assemblyReplacement = (com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe)recipe(helper, "replace_assembly");
        check(assemblyReplacement.resultPool.getFirst().getStack().getCount() == 2 && assemblyReplacement.resultPool.getFirst().getChance() == 1, "assembly replacement defaults weight to one");
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void descriptorRecipesExecuteNativeChanceTimingAndNetwork(GameTestHelper helper) {
        var nativeRecipe = machine(helper, "execution");
        var registry = helper.getLevel().registryAccess();
        var ops = registry.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = net.quiwh.cwi.machine.recipe.MachineRecipes.MIXING.serializer().codec().codec().encodeStart(ops, nativeRecipe).getOrThrow();
        var copy = net.quiwh.cwi.machine.recipe.MachineRecipes.MIXING.serializer().codec().codec().parse(ops, encoded).getOrThrow();
        check(copy.fingerprint(registry).equals(nativeRecipe.fingerprint(registry)), "descriptor JSON round trip");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registry);
        try {
            net.quiwh.cwi.machine.recipe.MachineRecipes.MIXING.serializer().streamCodec().encode(buffer, nativeRecipe);
            var network = net.quiwh.cwi.machine.recipe.MachineRecipes.MIXING.serializer().streamCodec().decode(buffer);
            check(network.fingerprint(registry).equals(nativeRecipe.fingerprint(registry)) && buffer.readableBytes() == 0, "descriptor network round trip");
        } finally { buffer.release(); }
        for (int seed = 0; seed < 20; seed++) {
            var items = new net.neoforged.neoforge.items.ItemStackHandler(4);
            items.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 2));
            var fluids = new CallableTanks();
            fluids.setFluid(0, new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 500));
            var random = net.minecraft.util.RandomSource.create(seed);
            check(net.quiwh.cwi.machine.RecipeTransaction.matches(nativeRecipe, items, fluids, 1), "descriptor recipe native match");
            check(net.quiwh.cwi.machine.RecipeTransaction.tick(nativeRecipe, items, fluids, 1, random), "per-tick input executes");
            check(fluids.getFluidInTank(0).getAmount() == 250 && items.getStackInSlot(0).getCount() == 2, "tick consumes fluid; zero-chance catalyst remains");
            var expected = net.minecraft.util.RandomSource.create(seed);
            int expectedItems = new com.simibubi.create.content.processing.recipe.ProcessingOutput(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLAY_BALL, 2), .25F).rollOutput(expected).getCount();
            int expectedFluid = expected.nextFloat() < .5F ? 75 : 0;
            check(net.quiwh.cwi.machine.RecipeTransaction.complete(nativeRecipe, items, fluids, 1, random), "ordinary descriptor transaction");
            int actualItems = 0; for (int slot = 0; slot < items.getSlots(); slot++) if (items.getStackInSlot(slot).is(net.minecraft.world.item.Items.CLAY_BALL)) actualItems += items.getStackInSlot(slot).getCount();
            check(actualItems == expectedItems && fluids.getFluidInTank(1).getAmount() == expectedFluid, "native per-item output and whole-fluid rolls retain order");
        }
        var incubator = machine(helper, "incubating");
        var items = new net.neoforged.neoforge.items.ItemStackHandler(3);
        items.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
        for (int i = 0; i < 3; i++) check(net.quiwh.cwi.machine.RecipeTransaction.tick(incubator, items, null, 1, net.minecraft.util.RandomSource.create(i)), "incubator tick output");
        int plates = 0, products = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (items.getStackInSlot(slot).is(net.minecraft.world.item.Items.GLASS_BOTTLE)) plates += items.getStackInSlot(slot).getCount();
            if (items.getStackInSlot(slot).is(net.minecraft.world.item.Items.CLAY_BALL)) products += items.getStackInSlot(slot).getCount();
        }
        check(plates == 1 && products == 3, "incubator preserves catalyst and emits once per working tick; plates=" + plates + " outputs=" + products);
        helper.succeed();
    }
    private static class CallableTanks implements net.quiwh.cwi.machine.MutableRecipeFluids {
        private final net.neoforged.neoforge.fluids.FluidStack[] stacks = {net.neoforged.neoforge.fluids.FluidStack.EMPTY, net.neoforged.neoforge.fluids.FluidStack.EMPTY};
        public int getTanks() { return 2; }
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) { return stacks[tank]; }
        public void setFluid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) { stacks[tank] = stack.copy(); }
        public int getTankCapacity(int tank) { return 1000; }
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack fluid) { return tank == 0 ? fluid.is(net.minecraft.world.level.material.Fluids.WATER) : fluid.is(net.minecraft.world.level.material.Fluids.LAVA); }
        public int fill(net.neoforged.neoforge.fluids.FluidStack stack, FluidAction action) { return 0; }
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack stack, FluidAction action) { return net.neoforged.neoforge.fluids.FluidStack.EMPTY; }
        public net.neoforged.neoforge.fluids.FluidStack drain(int amount, FluidAction action) { return net.neoforged.neoforge.fluids.FluidStack.EMPTY; }
    }
    static MachineRecipe machine(GameTestHelper helper, String id) { return (MachineRecipe)recipe(helper, id); }
    static net.minecraft.world.item.crafting.Recipe<?> recipe(GameTestHelper helper, String id) {
        return helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:callable/" + id)).orElseThrow().value();
    }
    static com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> processing(GameTestHelper helper, String id) {
        return (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?>)recipe(helper, id);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
