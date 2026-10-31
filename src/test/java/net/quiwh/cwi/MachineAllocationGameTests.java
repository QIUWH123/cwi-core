package net.quiwh.cwi;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.quiwh.cwi.machine.MutableRecipeFluids;
import net.quiwh.cwi.machine.RecipeTransaction;
import net.quiwh.cwi.machine.OutputAllocationContractTest;
import net.quiwh.cwi.machine.InputConsumptionContractTest;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class MachineAllocationGameTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void outputAllocationOracle(GameTestHelper helper) {
        OutputAllocationContractTest.verify();
        InputConsumptionContractTest.verify();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void constrainedOutputsAndTickReservations(GameTestHelper helper) {
        verifyTransactions();
        helper.succeed();
    }

    public static void verifyTransactions() {
        var items = new ItemStackHandler(3) {
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0 && (stack.is(Items.GOLD_INGOT) || stack.is(Items.COPPER_INGOT))
                        || slot == 1 && stack.is(Items.GOLD_INGOT);
            }
        };
        items.setStackInSlot(2, new ItemStack(Items.IRON_INGOT));
        var recipe = recipe(List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1, false)),
                List.of(), List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1, false),
                        new MachineRecipe.ItemOutput(new ItemStack(Items.COPPER_INGOT), 1, false)), List.of());
        check(RecipeTransaction.matches(recipe, items, null, 1), "feasible constrained item outputs rejected");
        check(items.getStackInSlot(0).isEmpty() && items.getStackInSlot(2).getCount() == 1, "simulation mutated items");
        check(RecipeTransaction.complete(recipe, items, null, 1, RandomSource.create(1)), "constrained output commit rejected");
        check(items.getStackInSlot(0).is(Items.COPPER_INGOT) && items.getStackInSlot(1).is(Items.GOLD_INGOT)
                && items.getStackInSlot(2).isEmpty(), "wrong output assignment or consumption");

        var ticking = new ItemStackHandler(2);
        ticking.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        ticking.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        var fluids = new Tanks();
        fluids.setFluid(0, new FluidStack(Fluids.WATER, 100));
        fluids.setFluid(1, new FluidStack(Fluids.LAVA, 100));
        var tickRecipe = recipe(List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1, false),
                        new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1, 1, true)),
                List.of(new MachineRecipe.FluidInput(SizedFluidIngredient.of(Fluids.WATER, 100), 1, false),
                        new MachineRecipe.FluidInput(new SizedFluidIngredient(FluidIngredient.of(Fluids.WATER, Fluids.LAVA), 100), 1, true)),
                List.of(), List.of());
        check(RecipeTransaction.tick(tickRecipe, ticking, fluids, 1, RandomSource.create(1)), "tick transaction failed");
        check(ticking.getStackInSlot(0).is(Items.IRON_INGOT) && ticking.getStackInSlot(1).isEmpty(), "tick stole specific ordinary item");
        check(fluids.getFluidInTank(0).is(Fluids.WATER) && fluids.getFluidInTank(0).getAmount() == 100
                && fluids.getFluidInTank(1).isEmpty(), "tick stole specific ordinary fluid");
        fluids.setFluid(0, FluidStack.EMPTY); fluids.setFluid(1, FluidStack.EMPTY);
        var outputFluids = recipe(List.of(), List.of(new MachineRecipe.FluidInput(SizedFluidIngredient.of(Fluids.WATER, 100), 0, false)),
                List.of(), List.of(new MachineRecipe.FluidOutput(new FluidStack(Fluids.WATER, 250), 1, false),
                        new MachineRecipe.FluidOutput(new FluidStack(Fluids.LAVA, 500), 1, false)));
        // The catalyst lives in a third input-only tank; destinations accept both/water-only respectively.
        fluids.setFluid(2, new FluidStack(Fluids.WATER, 100));
        check(RecipeTransaction.complete(outputFluids, null, fluids, 1, RandomSource.create(1)), "feasible constrained fluid outputs rejected");
        check(fluids.getFluidInTank(0).is(Fluids.LAVA) && fluids.getFluidInTank(0).getAmount() == 500
                && fluids.getFluidInTank(1).is(Fluids.WATER) && fluids.getFluidInTank(1).getAmount() == 250
                && fluids.getFluidInTank(2).getAmount() == 100, "fluid assignment lost catalyst/output");

        var full = new ItemStackHandler(6);
        full.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        full.setStackInSlot(1, new ItemStack(Items.IRON_INGOT));
        for (int slot = 2; slot < 6; slot++) full.setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
        var exchange = recipe(List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1, false)), List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1, false)), List.of());
        check(RecipeTransaction.matches(exchange, full, null, 1), "input allocation must free the partly filled stack for a different output");
        check(RecipeTransaction.complete(exchange, full, null, 1, RandomSource.create(1)), "joint input/output item commit");
        check(full.getStackInSlot(0).getCount() == 64 && full.getStackInSlot(1).is(Items.GOLD_INGOT), "wrong input stack consumed for output capacity");

        var fullFluids = new Tanks() {
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        };
        fullFluids.setFluid(0, new FluidStack(Fluids.WATER, 1000));
        fullFluids.setFluid(1, new FluidStack(Fluids.WATER, 100));
        fullFluids.setFluid(2, new FluidStack(Fluids.LAVA, 1000));
        var fluidExchange = recipe(List.of(), List.of(new MachineRecipe.FluidInput(SizedFluidIngredient.of(Fluids.WATER, 100), 1, false)),
                List.of(), List.of(new MachineRecipe.FluidOutput(new FluidStack(Fluids.LAVA, 100), 1, false)));
        check(RecipeTransaction.complete(fluidExchange, null, fullFluids, 1, RandomSource.create(1)), "input allocation must free the partly filled tank");
        check(fullFluids.getFluidInTank(0).getAmount() == 1000 && fullFluids.getFluidInTank(1).is(Fluids.LAVA)
                && fullFluids.getFluidInTank(1).getAmount() == 100, "wrong tank consumed for output capacity");

        var finalTick = new ItemStackHandler(9) {
            @Override public boolean isItemValid(int slot, ItemStack stack) { return slot != 8 || stack.is(Items.GLASS_BOTTLE); }
        };
        for (int slot = 0; slot < 7; slot++) finalTick.setStackInSlot(slot, new ItemStack(Items.DIAMOND));
        finalTick.setStackInSlot(8, new ItemStack(Items.GLASS_BOTTLE));
        var finalRecipe = recipe(List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.GLASS_BOTTLE), 1, 1, false)), List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1, false),
                        new MachineRecipe.ItemOutput(new ItemStack(Items.GLASS_BOTTLE), 1, true)), List.of());
        check(RecipeTransaction.finishTick(finalRecipe, finalTick, null, 1, RandomSource.create(1)), "last tick must consume ordinary inputs and place both outputs atomically");
        check(finalTick.getStackInSlot(7).is(Items.GOLD_INGOT) && finalTick.getStackInSlot(8).is(Items.GLASS_BOTTLE),
                "final tick lost ordinary output or per-tick output");

        finalTick.setStackInSlot(7, ItemStack.EMPTY);
        finalTick.setStackInSlot(8, new ItemStack(Items.GLASS_BOTTLE, 64));
        check(!RecipeTransaction.tick(finalRecipe, finalTick, null, 1, RandomSource.create(1)),
                "intermediate tick must preserve ordinary output capacity");
        check(finalTick.getStackInSlot(7).isEmpty() && finalTick.getStackInSlot(8).getCount() == 64,
                "blocked intermediate tick changed storage");

        var alternate = new ItemStackHandler(3) {
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0 || slot == 1 && stack.is(Items.GLASS_BOTTLE) || slot == 2 && stack.is(Items.IRON_INGOT);
            }
        };
        alternate.setStackInSlot(2, new ItemStack(Items.IRON_INGOT));
        var alternateRecipe = recipe(List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 1, 1, false)), List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1, false),
                        new MachineRecipe.ItemOutput(new ItemStack(Items.GLASS_BOTTLE), 1, true)), List.of());
        check(RecipeTransaction.tick(alternateRecipe, alternate, null, 1, RandomSource.create(1)),
                "tick must search alternate output destinations to preserve completion");
        check(alternate.getStackInSlot(0).isEmpty() && alternate.getStackInSlot(1).is(Items.GLASS_BOTTLE)
                && alternate.getStackInSlot(2).is(Items.IRON_INGOT), "tick output blocked the only ordinary output destination");
        check(RecipeTransaction.complete(alternateRecipe, alternate, null, 1, RandomSource.create(1))
                && alternate.getStackInSlot(0).is(Items.GOLD_INGOT), "reserved ordinary completion failed");

        var alternateFluids = new Tanks();
        alternateFluids.setFluid(2, new FluidStack(Fluids.WATER, 100));
        var alternateFluidRecipe = recipe(List.of(), List.of(new MachineRecipe.FluidInput(SizedFluidIngredient.of(Fluids.WATER, 100), 1, false)),
                List.of(), List.of(new MachineRecipe.FluidOutput(new FluidStack(Fluids.LAVA, 500), 1, false),
                        new MachineRecipe.FluidOutput(new FluidStack(Fluids.WATER, 250), 1, true)));
        check(RecipeTransaction.tick(alternateFluidRecipe, null, alternateFluids, 1, RandomSource.create(1)),
                "fluid tick must search destinations that preserve completion");
        check(alternateFluids.getFluidInTank(0).isEmpty() && alternateFluids.getFluidInTank(1).is(Fluids.WATER)
                && alternateFluids.getFluidInTank(2).getAmount() == 100, "fluid tick blocked ordinary output tank");

        var saturated = new ItemStackHandler(6);
        for (int slot = 0; slot < 6; slot++) saturated.setStackInSlot(slot, new ItemStack(Items.IRON_INGOT, 64));
        var overflowing = recipe(List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 128, 1, false)), List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.IRON_INGOT, 129), 1, false)), List.of());
        long start = System.nanoTime();
        check(!RecipeTransaction.matches(overflowing, saturated, null, 1), "net-positive output cannot fit saturated item storage");
        check(System.nanoTime() - start < 1_000_000_000L, "saturated item rejection exhausted input allocations");
        for (int slot = 0; slot < 6; slot++) check(saturated.getStackInSlot(slot).getCount() == 64, "capacity rejection mutated storage");
        var indivisible = recipe(List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.IRON_INGOT), 65, 1, false)), List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.GOLD_INGOT, 65), 1, false)), List.of());
        start = System.nanoTime();
        check(!RecipeTransaction.matches(indivisible, saturated, null, 1), "new product requires two completely emptied destinations");
        check(System.nanoTime() - start < 1_000_000_000L, "indivisible destination rejection exhausted input allocations");

        var saturatedFluids = new Tanks() {
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        };
        for (int tank = 0; tank < 3; tank++) saturatedFluids.setFluid(tank, new FluidStack(Fluids.WATER, 1000));
        var overflowingFluids = recipe(List.of(), List.of(new MachineRecipe.FluidInput(SizedFluidIngredient.of(Fluids.WATER, 1000), 1, false)),
                List.of(), List.of(new MachineRecipe.FluidOutput(new FluidStack(Fluids.WATER, 1001), 1, false)));
        start = System.nanoTime();
        check(!RecipeTransaction.matches(overflowingFluids, null, saturatedFluids, 1), "net-positive fluid output cannot fit saturated tanks");
        check(System.nanoTime() - start < 1_000_000_000L, "saturated fluid rejection exhausted input allocations");
    }

    private static MachineRecipe recipe(List<MachineRecipe.ItemInput> items, List<MachineRecipe.FluidInput> fluids,
                                        List<MachineRecipe.ItemOutput> itemOutputs, List<MachineRecipe.FluidOutput> fluidOutputs) {
        return new MachineRecipe(MachineRecipes.MIXING, items, fluids, itemOutputs, fluidOutputs, 20, 0, 0, HeatCondition.NONE);
    }
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }

    private static class Tanks implements MutableRecipeFluids {
        private final FluidStack[] tanks = {FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY};
        @Override public int getTanks() { return tanks.length; }
        @Override public FluidStack getFluidInTank(int tank) { return tanks[tank]; }
        @Override public int getTankCapacity(int tank) { return 1000; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 || tank == 1 && stack.is(Fluids.WATER); }
        @Override public void setFluid(int tank, FluidStack stack) { tanks[tank] = stack.copy(); }
        @Override public int fill(FluidStack stack, FluidAction action) { throw new AssertionError("Atomic recipes must commit owned storage directly"); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { throw new AssertionError("Atomic recipes must commit owned storage directly"); }
        @Override public FluidStack drain(int amount, FluidAction action) { throw new AssertionError("Atomic recipes must commit owned storage directly"); }
    }
}
