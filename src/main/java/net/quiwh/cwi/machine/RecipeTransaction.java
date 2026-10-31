package net.quiwh.cwi.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.quiwh.cwi.machine.recipe.MachineRecipe;

/** Plan against private snapshots, then commit changed slots once, on the owning server thread. */
public final class RecipeTransaction {
    private enum Phase { ALL, ORDINARY, TICK }

    public static boolean matches(MachineRecipe recipe, IItemHandler items, IFluidHandler fluids, int batch) {
        return plan(recipe, items, fluids, batch, Phase.ALL, null) != null;
    }
    public static int maxBatch(MachineRecipe recipe, IItemHandler items, IFluidHandler fluids, int limit) {
        for (int count = Math.min(64, limit); count > 0; count--) if (matches(recipe, items, fluids, count)) return count;
        return 0;
    }
    public static boolean complete(MachineRecipe recipe, IItemHandler items, IFluidHandler fluids, int batch, RandomSource random) {
        return execute(recipe, items, fluids, batch, Phase.ORDINARY, random);
    }
    public static boolean tick(MachineRecipe recipe, IItemHandler items, IFluidHandler fluids, int batch, RandomSource random) {
        return execute(recipe, items, fluids, batch, Phase.TICK, random);
    }
    /** The last working tick commits ordinary and per-tick IO together, sharing freed input capacity. */
    public static boolean finishTick(MachineRecipe recipe, IItemHandler items, IFluidHandler fluids, int batch, RandomSource random) {
        return execute(recipe, items, fluids, batch, Phase.ALL, random);
    }

    private static boolean execute(MachineRecipe recipe, IItemHandler items, IFluidHandler fluids, int batch, Phase phase, RandomSource random) {
        // A failed simulation never rolls chance, consumes material, or produces a partial result.
        if (plan(recipe, items, fluids, batch, phase, null) == null) return false;
        Plan plan = plan(recipe, items, fluids, batch, phase, random);
        if (plan == null) return false;
        if (items != null && !(items instanceof IItemHandlerModifiable)) return false;
        if (fluids != null && !(fluids instanceof MutableRecipeFluids)) return false;
        for (int i = 0; i < plan.items.length; i++)
            if (!ItemStack.matches(plan.items[i], items.getStackInSlot(i))) ((IItemHandlerModifiable)items).setStackInSlot(i, plan.items[i]);
        for (int i = 0; i < plan.fluids.length; i++)
            if (!FluidStack.matches(plan.fluids[i], fluids.getFluidInTank(i))) ((MutableRecipeFluids)fluids).setFluid(i, plan.fluids[i]);
        return true;
    }

    private static boolean includes(Phase phase, boolean perTick) { return phase == Phase.ALL || (phase == Phase.TICK) == perTick; }
    private static int count(int amount, float chance, int batch, RandomSource random) {
        if (chance == 0) return 0;
        int copies = 0;
        for (int i = 0; i < batch; i++) if (random == null || chance >= 1 || random.nextFloat() < chance) copies++;
        return Math.multiplyExact(amount, copies);
    }

    private static int itemOutputCount(int amount, float chance, int batch, RandomSource random) {
        int maximum = Math.multiplyExact(amount, batch);
        if (chance == 0) return 0;
        if (random == null || chance >= 1) return maximum;
        int produced = maximum;
        // Same comparison and roll order as Create's ProcessingOutput, including counted batches.
        for (int i = 0; i < maximum; i++) if (random.nextFloat() > chance) produced--;
        return produced;
    }

    private static Plan plan(MachineRecipe recipe, IItemHandler items, IFluidHandler fluids, int batch, Phase phase, RandomSource random) {
        if (batch < 1 || batch > 64) return null;
        ItemStack[] itemState = new ItemStack[items == null ? 0 : items.getSlots()];
        FluidStack[] fluidState = new FluidStack[fluids == null ? 0 : fluids.getTanks()];
        for (int i = 0; i < itemState.length; i++) itemState[i] = items.getStackInSlot(i).copy();
        for (int i = 0; i < fluidState.length; i++) fluidState[i] = fluids.getFluidInTank(i).copy();
        try {
            int[] itemAvailable = new int[itemState.length], fluidAvailable = new int[fluidState.length];
            for (int i = 0; i < itemState.length; i++) itemAvailable[i] = items.extractItem(i, itemState[i].getCount(), true).getCount();
            for (int i = 0; i < fluidState.length; i++) fluidAvailable[i] = fluids instanceof MutableRecipeFluids mutable && mutable.canExtractTank(i) ? fluidState[i].getAmount() : 0;
            ItemStack[] afterItems = planItems(recipe, items, itemState, itemAvailable, batch, phase, random);
            if (afterItems == null) return null;
            FluidStack[] afterFluids = planFluids(recipe, fluids, fluidState, fluidAvailable, batch, phase, random);
            return afterFluids == null ? null : new Plan(afterItems, afterFluids);
        } catch (ArithmeticException overflow) { return null; }
    }

    private static ItemStack[] planItems(MachineRecipe recipe, IItemHandler items, ItemStack[] itemState,
                                         int[] itemAvailable, int batch, Phase phase, RandomSource random) {
            // Tick inputs reserve ordinary ingredients. Tick outputs must also leave ordinary completion possible.
            var itemInputs = recipe.itemInputs().stream().filter(in -> phase == Phase.TICK || includes(phase, in.perTick())).toList();
            int[] itemRequired = new int[itemInputs.size()], itemConsumed = new int[itemInputs.size()];
            boolean[][] itemAccepts = new boolean[itemInputs.size()][itemState.length];
            for (int d = 0; d < itemInputs.size(); d++) {
                var input = itemInputs.get(d); itemRequired[d] = Math.multiplyExact(input.count(), batch);
                for (int s = 0; s < itemState.length; s++) itemAccepts[d][s] = input.ingredient().test(itemState[s]);
                if (includes(phase, input.perTick())) itemConsumed[d] = random == null && input.chance() < 1 ? 0 : count(input.count(), input.chance(), batch, random);
            }
            var itemOutputs = new ArrayList<ItemStack>();
            for (var output : recipe.itemOutputs()) if (includes(phase, output.perTick())) {
                int produced = itemOutputCount(output.stack().getCount(), output.chance(), batch, random);
                if (produced > 0) itemOutputs.add(output.stack().copyWithCount(produced));
            }
            if (!itemCapacityPossible(itemOutputs, items, itemState, itemAvailable, itemConsumed, itemAccepts)) return null;
            ItemStack[][] plannedItems = new ItemStack[1][];
            Predicate<int[]> place = allocation -> {
                ItemStack[] after = new ItemStack[itemState.length];
                for (int slot = 0; slot < after.length; slot++) { after[slot] = itemState[slot].copy(); after[slot].shrink(allocation[slot]); }
                int[] reservedAvailable = remaining(itemAvailable, allocation);
                Predicate<ItemStack[]> future = phase != Phase.TICK ? null : next ->
                        planItems(recipe, items, next, reservedAvailable, batch, Phase.ORDINARY, null) != null;
                ItemStack[] inserted = insertItems(itemOutputs, items, after, future);
                if (inserted == null) return false;
                plannedItems[0] = inserted;
                return true;
            };
            Predicate<int[]> optimistic = phase != Phase.TICK ? place : allocation -> {
                ItemStack[] after = new ItemStack[itemState.length];
                for (int slot = 0; slot < after.length; slot++) { after[slot] = itemState[slot].copy(); after[slot].shrink(allocation[slot]); }
                return insertItems(itemOutputs, items, after, null) != null;
            };
            return InputAllocation.consumeForOutputs(itemAvailable, itemRequired, itemConsumed, itemAccepts, place, optimistic) == null ? null : plannedItems[0];
    }

    private static FluidStack[] planFluids(MachineRecipe recipe, IFluidHandler fluids, FluidStack[] fluidState,
                                           int[] fluidAvailable, int batch, Phase phase, RandomSource random) {
            var fluidInputs = recipe.fluidInputs().stream().filter(in -> phase == Phase.TICK || includes(phase, in.perTick())).toList();
            int[] fluidRequired = new int[fluidInputs.size()], fluidConsumed = new int[fluidInputs.size()];
            boolean[][] fluidAccepts = new boolean[fluidInputs.size()][fluidState.length];
            for (int d = 0; d < fluidInputs.size(); d++) {
                var input = fluidInputs.get(d); fluidRequired[d] = Math.multiplyExact(input.ingredient().amount(), batch);
                for (int s = 0; s < fluidState.length; s++) fluidAccepts[d][s] = input.ingredient().ingredient().test(fluidState[s]);
                if (includes(phase, input.perTick())) fluidConsumed[d] = random == null && input.chance() < 1 ? 0 : count(input.ingredient().amount(), input.chance(), batch, random);
            }
            var fluidOutputs = new ArrayList<FluidStack>();
            for (var output : recipe.fluidOutputs()) if (includes(phase, output.perTick())) {
                int produced = count(output.stack().getAmount(), output.chance(), batch, random);
                if (produced > 0) fluidOutputs.add(output.stack().copyWithAmount(produced));
            }
            if (!fluidCapacityPossible(fluidOutputs, fluids, fluidState, fluidAvailable, fluidConsumed, fluidAccepts)) return null;
            FluidStack[][] plannedFluids = new FluidStack[1][];
            Predicate<int[]> place = allocation -> {
                FluidStack[] after = new FluidStack[fluidState.length];
                for (int tank = 0; tank < after.length; tank++) { after[tank] = fluidState[tank].copy(); after[tank].shrink(allocation[tank]); }
                int[] reservedAvailable = remaining(fluidAvailable, allocation);
                Predicate<FluidStack[]> future = phase != Phase.TICK ? null : next ->
                        planFluids(recipe, fluids, next, reservedAvailable, batch, Phase.ORDINARY, null) != null;
                FluidStack[] inserted = insertFluids(fluidOutputs, fluids, after, future);
                if (inserted == null) return false;
                plannedFluids[0] = inserted;
                return true;
            };
            Predicate<int[]> optimistic = phase != Phase.TICK ? place : allocation -> {
                FluidStack[] after = new FluidStack[fluidState.length];
                for (int tank = 0; tank < after.length; tank++) { after[tank] = fluidState[tank].copy(); after[tank].shrink(allocation[tank]); }
                return insertFluids(fluidOutputs, fluids, after, null) != null;
            };
            return InputAllocation.consumeForOutputs(fluidAvailable, fluidRequired, fluidConsumed, fluidAccepts, place, optimistic) == null ? null : plannedFluids[0];
    }

    private static int[] remaining(int[] available, int[] consumed) {
        int[] result = available.clone();
        for (int slot = 0; slot < result.length; slot++) result[slot] -= consumed[slot];
        return result;
    }

    private static int[] maximumConsumption(int[] available, int[] consumed, boolean[][] accepts) {
        int[] maximum = new int[available.length];
        for (int slot = 0; slot < available.length; slot++) {
            long possible = 0;
            for (int demand = 0; demand < consumed.length; demand++) if (accepts[demand][slot]) possible += consumed[demand];
            maximum[slot] = (int) Math.min(available[slot], possible);
        }
        return maximum;
    }

    private static boolean itemCapacityPossible(List<ItemStack> outputs, IItemHandler items, ItemStack[] state,
                                                 int[] available, int[] consumed, boolean[][] accepts) {
        long budget = 0, produced = 0;
        for (int amount : consumed) budget += amount;
        int[] occupied = new int[state.length], limits = new int[state.length];
        for (int slot = 0; slot < state.length; slot++) {
            occupied[slot] = state[slot].getCount();
            limits[slot] = Math.min(items.getSlotLimit(slot), state[slot].getMaxStackSize());
            for (ItemStack output : outputs) if (items.isItemValid(slot, output))
                limits[slot] = Math.max(limits[slot], Math.min(items.getSlotLimit(slot), output.getMaxStackSize()));
        }
        for (ItemStack output : outputs) produced += output.getCount();
        if (!OutputAllocation.hasAggregateSpace(occupied, limits, budget, produced)) return false;
        int[] maximum = maximumConsumption(available, consumed, accepts);
        for (ItemStack product : outputs) {
            long needed = 0, free = 0, matchingGain = 0;
            var costs = new ArrayList<Integer>();
            var gains = new ArrayList<Integer>();
            for (ItemStack output : outputs) if (ItemStack.isSameItemSameComponents(product, output)) needed += output.getCount();
            for (int slot = 0; slot < state.length; slot++) if (items.isItemValid(slot, product)) {
                int limit = Math.min(items.getSlotLimit(slot), product.getMaxStackSize());
                if (state[slot].isEmpty()) free += limit;
                else if (ItemStack.isSameItemSameComponents(state[slot], product)) {
                    free += Math.max(0, limit - occupied[slot]);
                    matchingGain += Math.min(maximum[slot], limit);
                } else if (maximum[slot] >= occupied[slot] && budget >= occupied[slot]) { costs.add(occupied[slot]); gains.add(limit); }
            }
            if (!OutputAllocation.hasProductSpace(needed, free, matchingGain, budget,
                    costs.stream().mapToInt(Integer::intValue).toArray(), gains.stream().mapToInt(Integer::intValue).toArray())) return false;
        }
        return true;
    }

    private static boolean fluidCapacityPossible(List<FluidStack> outputs, IFluidHandler fluids, FluidStack[] state,
                                                  int[] available, int[] consumed, boolean[][] accepts) {
        long budget = 0, produced = 0;
        for (int amount : consumed) budget += amount;
        int[] occupied = new int[state.length], limits = new int[state.length];
        for (int tank = 0; tank < state.length; tank++) { occupied[tank] = state[tank].getAmount(); limits[tank] = fluids.getTankCapacity(tank); }
        for (FluidStack output : outputs) produced += output.getAmount();
        if (!OutputAllocation.hasAggregateSpace(occupied, limits, budget, produced)) return false;
        int[] maximum = maximumConsumption(available, consumed, accepts);
        for (FluidStack product : outputs) {
            long needed = 0, free = 0, matchingGain = 0;
            var costs = new ArrayList<Integer>();
            var gains = new ArrayList<Integer>();
            for (FluidStack output : outputs) if (FluidStack.isSameFluidSameComponents(product, output)) needed += output.getAmount();
            for (int tank = 0; tank < state.length; tank++)
                if (fluids instanceof MutableRecipeFluids mutable && mutable.canInsertTank(tank, product)) {
                    int limit = fluids.getTankCapacity(tank);
                    if (state[tank].isEmpty()) free += limit;
                    else if (FluidStack.isSameFluidSameComponents(state[tank], product)) {
                        free += Math.max(0, limit - occupied[tank]);
                        matchingGain += Math.min(maximum[tank], limit);
                    } else if (maximum[tank] >= occupied[tank] && budget >= occupied[tank]) { costs.add(occupied[tank]); gains.add(limit); }
                }
            if (!OutputAllocation.hasProductSpace(needed, free, matchingGain, budget,
                    costs.stream().mapToInt(Integer::intValue).toArray(), gains.stream().mapToInt(Integer::intValue).toArray())) return false;
        }
        return true;
    }

    private static ItemStack[] insertItems(List<ItemStack> outputs, IItemHandler items, ItemStack[] state, Predicate<ItemStack[]> acceptable) {
        var products = new ArrayList<ItemStack>();
        var amounts = new ArrayList<Long>();
        for (var output : outputs) {
            int amount = output.getCount();
            if (amount == 0) continue;
            int product = 0;
            while (product < products.size() && !ItemStack.isSameItemSameComponents(products.get(product), output)) product++;
            if (product == products.size()) { products.add(output); amounts.add((long) amount); }
            else amounts.set(product, amounts.get(product) + amount);
        }
        long[] required = new long[products.size()];
        int[][] capacities = new int[products.size()][state.length];
        for (int product = 0; product < products.size(); product++) {
            ItemStack stack = products.get(product);
            required[product] = amounts.get(product);
            for (int slot = 0; slot < state.length; slot++) {
                if (!items.isItemValid(slot, stack)) continue;
                int limit = Math.min(items.getSlotLimit(slot), stack.getMaxStackSize());
                if (state[slot].isEmpty()) capacities[product][slot] = Math.max(0, limit);
                else if (ItemStack.isSameItemSameComponents(state[slot], stack)) {
                    capacities[product][slot] = Math.max(0, limit - state[slot].getCount());
                }
            }
        }
        int[][] allocation = OutputAllocation.allocate(required, capacities, acceptable == null ? null : candidate ->
                acceptable.test(applyItems(products, state, candidate)));
        return allocation == null ? null : applyItems(products, state, allocation);
    }

    private static ItemStack[] applyItems(List<ItemStack> products, ItemStack[] state, int[][] allocation) {
        ItemStack[] result = new ItemStack[state.length];
        for (int slot = 0; slot < state.length; slot++) result[slot] = state[slot].copy();
        for (int product = 0; product < products.size(); product++) for (int slot = 0; slot < state.length; slot++)
            if (allocation[product][slot] > 0) result[slot] = products.get(product).copyWithCount(state[slot].getCount() + allocation[product][slot]);
        return result;
    }

    private static FluidStack[] insertFluids(List<FluidStack> outputs, IFluidHandler fluids, FluidStack[] state, Predicate<FluidStack[]> acceptable) {
        var products = new ArrayList<FluidStack>();
        var amounts = new ArrayList<Long>();
        for (var output : outputs) {
            int amount = output.getAmount();
            if (amount == 0) continue;
            int product = 0;
            while (product < products.size() && !FluidStack.isSameFluidSameComponents(products.get(product), output)) product++;
            if (product == products.size()) { products.add(output); amounts.add((long) amount); }
            else amounts.set(product, amounts.get(product) + amount);
        }
        long[] required = new long[products.size()];
        int[][] capacities = new int[products.size()][state.length];
        for (int product = 0; product < products.size(); product++) {
            FluidStack stack = products.get(product);
            required[product] = amounts.get(product);
            for (int tank = 0; tank < state.length; tank++) {
                if (!(fluids instanceof MutableRecipeFluids mutable) || !mutable.canInsertTank(tank, stack)) continue;
                int limit = fluids.getTankCapacity(tank);
                if (state[tank].isEmpty()) capacities[product][tank] = Math.max(0, limit);
                else if (FluidStack.isSameFluidSameComponents(state[tank], stack)) {
                    capacities[product][tank] = Math.max(0, limit - state[tank].getAmount());
                }
            }
        }
        int[][] allocation = OutputAllocation.allocate(required, capacities, acceptable == null ? null : candidate ->
                acceptable.test(applyFluids(products, state, candidate)));
        return allocation == null ? null : applyFluids(products, state, allocation);
    }

    private static FluidStack[] applyFluids(List<FluidStack> products, FluidStack[] state, int[][] allocation) {
        FluidStack[] result = new FluidStack[state.length];
        for (int tank = 0; tank < state.length; tank++) result[tank] = state[tank].copy();
        for (int product = 0; product < products.size(); product++) for (int tank = 0; tank < state.length; tank++)
            if (allocation[product][tank] > 0) result[tank] = products.get(product).copyWithAmount(state[tank].getAmount() + allocation[product][tank]);
        return result;
    }
    private record Plan(ItemStack[] items, FluidStack[] fluids) {}
    private RecipeTransaction() {}
}
