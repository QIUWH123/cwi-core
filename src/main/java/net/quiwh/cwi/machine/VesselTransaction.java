package net.quiwh.cwi.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.quiwh.cwi.machine.recipe.EntrySettings;
import net.quiwh.cwi.machine.recipe.MachineRecipe;

/** Shared transactional IO for the vessel and batched blast furnace. */
public final class VesselTransaction {
    private record Demand(Ingredient ingredient, int required, int consumed) {}
    private record Tool(MachineRecipe.ItemInput input, int uses, int index) {}
    private record Plan(ItemStack[] items, FluidStack[] fluids) {}

    public static boolean eligible(MachineRecipe recipe, IItemHandlerModifiable items, MutableRecipeFluids fluids, ServerLevel level) {
        return eligible(recipe, items, fluids, level, 1);
    }
    public static boolean eligible(MachineRecipe recipe, IItemHandlerModifiable items, MutableRecipeFluids fluids, ServerLevel level, int batch) {
        var preview = new VesselOperation(0); preview.prepare(1);
        return plan(recipe, preview, items, fluids, level, true, batch) != null;
    }
    public static boolean step(MachineRecipe recipe, VesselOperation operation, IItemHandlerModifiable items,
                               MutableRecipeFluids fluids, ServerLevel level) {
        return step(recipe, operation, items, fluids, level, 1);
    }
    public static boolean step(MachineRecipe recipe, VesselOperation operation, IItemHandlerModifiable items,
                               MutableRecipeFluids fluids, ServerLevel level, int batch) {
        try {
            var plan = plan(recipe, operation, items, fluids, level, false, batch);
            if (plan == null) return false;
            for (int i = 0; i < plan.items.length; i++) if (!ItemStack.matches(items.getStackInSlot(i), plan.items[i])) items.setStackInSlot(i, plan.items[i]);
            for (int i = 0; i < plan.fluids.length; i++) if (!FluidStack.matches(fluids.getFluidInTank(i), plan.fluids[i])) fluids.setFluid(i, plan.fluids[i]);
            operation.commit(recipe.periodic());
            return true;
        } catch (ArithmeticException invalidAmount) { return false; }
    }
    private static int due(EntrySettings settings, MachineRecipe recipe, VesselOperation operation, boolean preview) {
        return preview ? settings.periodic() ? 1 : 0 : operation.events(settings, recipe.getProcessingDuration());
    }
    private static boolean payOrdinary(MachineRecipe recipe, VesselOperation operation, boolean preview) {
        return preview || (recipe.periodic() ? !operation.paid() : operation.finishing());
    }
    private static int[] consumption(int amount, float chance, int events, net.minecraft.util.RandomSource random, boolean preview, int batch) {
        int spent = 0, required = 0;
        for (int n = 0; n < events; n++) {
            if (n % batch == 0) required = Math.max(required, Math.addExact(spent, Math.multiplyExact(amount, batch)));
            if (chance > 0 && (chance == 1 || preview || random.nextFloat() < chance)) spent = Math.addExact(spent, amount);
        }
        return new int[]{required, spent};
    }
    private static Plan plan(MachineRecipe recipe, VesselOperation operation, IItemHandlerModifiable items,
                             MutableRecipeFluids fluids, ServerLevel level, boolean preview, int batch) {
        if (batch < 1 || batch > 64) return null;
        try {
            boolean ordinary = payOrdinary(recipe, operation, preview), finish = preview || operation.finishing();
            ItemStack[] before = new ItemStack[items.getSlots()];
            for (int s = 0; s < before.length; s++) before[s] = items.getStackInSlot(s).copy();
            var demands = new ArrayList<Demand>(); var tools = new ArrayList<Tool>();
            for (int i = 0; i < recipe.itemInputs().size(); i++) {
                var input = recipe.itemInputs().get(i); var settings = input.settings();
                int events = Math.multiplyExact(batch, settings.periodic() ? due(settings, recipe, operation, preview) : ordinary ? 1 : 0);
                boolean reserve = settings.catalyst() || !settings.periodic() && !operation.paid();
                if (events == 0 && !reserve) continue;
                var random = operation.random(settings.periodic() ? 1000L + i * 4099L + operation.ticks() * 1000003L : 8000L + i);
                if (settings.tool()) {
                    int uses = 0;
                    for (int n = 0; n < events; n++) if (input.chance() == 1 || input.chance() > 0 && random.nextFloat() < input.chance()) uses++;
                    tools.add(new Tool(input, preview ? 0 : uses, i));
                } else if (settings.catalyst() || events == 0) demands.add(new Demand(input.ingredient(), Math.multiplyExact(input.count(), batch), 0));
                else {
                    int[] amounts = consumption(input.count(), input.chance(), events, random, preview, batch);
                    demands.add(new Demand(input.ingredient(), amounts[0], amounts[1]));
                }
            }
            var products = new ArrayList<ItemStack>();
            for (int i = 0; i < recipe.itemOutputs().size(); i++) {
                var output = recipe.itemOutputs().get(i);
                int events = Math.multiplyExact(batch, output.perTick() ? due(output.settings(), recipe, operation, preview) : finish ? 1 : 0);
                var random = operation.random(output.perTick() ? 2000L + i * 4099L + operation.ticks() * 1000003L : 3000L + i);
                int total = 0;
                for (int n = 0; n < events; n++) total = Math.addExact(total, preview ? output.chance() == 0 ? 0 : output.stack().getCount()
                        : new com.simibubi.create.content.processing.recipe.ProcessingOutput(output.stack(), output.chance()).rollOutput(random).getCount());
                if (total > 0) products.add(output.stack().copyWithCount(total));
            }
            if (finish) for (int i = 0; i < recipe.outputPools().size(); i++) for (int copy = 0; copy < batch; copy++)
                products.add(recipe.outputPools().get(i).roll(operation.random(4000L + i + copy * 104729L)));
            var afterFluids = fluids(recipe, operation, fluids, ordinary, finish, preview, batch);
            if (afterFluids == null) return null;
            ItemStack[] afterItems = tools(tools, 0, before, new boolean[before.length], demands, products, items, operation, level, preview);
            return afterItems == null ? null : new Plan(afterItems, afterFluids);
        } catch (ArithmeticException invalidAmount) { return null; }
    }
    private static boolean validTool(MachineRecipe.ItemInput input, ItemStack stack) {
        if (!input.ingredient().test(stack) || stack.getCount() != 1) return false;
        if (input.settings().damage() > 0) return stack.isDamageableItem() && !stack.has(DataComponents.UNBREAKABLE);
        var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy != null && energy.canExtract() && energy.extractEnergy(input.settings().energy(), true) == input.settings().energy();
    }
    private static ItemStack[] tools(List<Tool> tools, int index, ItemStack[] state, boolean[] reserved, List<Demand> demands,
                                     List<ItemStack> products, IItemHandlerModifiable items, VesselOperation operation, ServerLevel level, boolean preview) {
        if (index == tools.size()) return items(state, reserved, demands, products, items);
        var tool = tools.get(index);
        return toolUses(tools, index, tool, 0, -1, state, reserved, demands, products, items, operation, level, preview);
    }
    private static ItemStack[] toolUses(List<Tool> tools, int index, Tool tool, int use, int previousSlot, ItemStack[] state, boolean[] reserved,
                                        List<Demand> demands, List<ItemStack> products, IItemHandlerModifiable items,
                                        VesselOperation operation, ServerLevel level, boolean preview) {
        for (int slot = 0; slot < state.length; slot++) {
            if (previousSlot >= 0 && previousSlot != slot || reserved[slot] && slot != previousSlot || !validTool(tool.input, state[slot])
                    || items.extractItem(slot, 1, true).isEmpty()) continue;
            ItemStack[] after = copy(state); boolean[] held = reserved.clone(); held[slot] = true;
            if (preview && tool.input.settings().damage() > 0 && tool.input.chance() > 0
                    && tool.input.settings().damage() >= after[slot].getMaxDamage() - after[slot].getDamageValue()) {
                // Selection may use the space a broken tool frees. Native damage/enchantments are
                // evaluated only by the pending transaction, which still requires the actual result to fit.
                after[slot] = ItemStack.EMPTY;
            } else if (tool.uses > use) {
                var settings = tool.input.settings();
                if (settings.damage() > 0) after[slot] = damage(after[slot], settings.damage(), tool.index + ":" + use, operation, level);
                else {
                    var energy = after[slot].getCapability(Capabilities.EnergyStorage.ITEM);
                    if (energy == null || energy.extractEnergy(settings.energy(), false) != settings.energy()) continue;
                }
            }
            ItemStack[] result = tool.uses > use + 1
                    ? toolUses(tools, index, tool, use + 1, after[slot].isEmpty() ? -1 : slot, after, held, demands, products, items, operation, level, preview)
                    : tools(tools, index + 1, after, held, demands, products, items, operation, level, preview);
            if (result != null) return result;
        }
        return null;
    }
    private static ItemStack damage(ItemStack stack, int amount, String key, VesselOperation operation, ServerLevel level) {
        var decisions = operation.toolRolls.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < decisions.size(); i++) {
            var stored = decisions.getCompound(i);
            if (ItemStack.matches(ItemStack.parseOptional(level.registryAccess(), stored.getCompound("Before")), stack))
                return ItemStack.parseOptional(level.registryAccess(), stored.getCompound("After"));
        }
        var after = stack.copy();
        after.hurtAndBreak(amount, level, (net.minecraft.server.level.ServerPlayer)null, item -> {});
        var roll = new CompoundTag(); roll.put("Before", stack.saveOptional(level.registryAccess())); roll.put("After", after.saveOptional(level.registryAccess()));
        decisions.add(roll); operation.toolRolls.put(key, decisions); return after;
    }
    private static ItemStack[] items(ItemStack[] state, boolean[] reserved, List<Demand> demands, List<ItemStack> products, IItemHandlerModifiable items) {
        int[] available = new int[state.length], required = new int[demands.size()], consumed = new int[demands.size()];
        boolean[][] accepts = new boolean[demands.size()][state.length];
        for (int s = 0; s < state.length; s++) available[s] = reserved[s] ? 0 : Math.min(state[s].getCount(), items.extractItem(s, state[s].getCount(), true).getCount());
        for (int d = 0; d < demands.size(); d++) {
            required[d] = demands.get(d).required; consumed[d] = demands.get(d).consumed;
            for (int s = 0; s < state.length; s++) accepts[d][s] = demands.get(d).ingredient.test(state[s]);
        }
        ItemStack[][] result = new ItemStack[1][];
        Predicate<int[]> fits = allocation -> {
            ItemStack[] after = copy(state);
            for (int s = 0; s < state.length; s++) after[s].shrink(allocation[s]);
            var inserted = RecipeTransaction.insertItems(RecipeTransaction.withRemainders(products, state, allocation), items, after, null);
            if (inserted == null) return false; result[0] = inserted; return true;
        };
        // Upper bounds may remove catalysts in an optimistic probe; only the flow-validated allocation is committed.
        return InputAllocation.consumeForOutputs(available, required, consumed, accepts, fits) == null ? null : result[0];
    }
    private static ItemStack[] copy(ItemStack[] state) { return java.util.Arrays.stream(state).map(ItemStack::copy).toArray(ItemStack[]::new); }
    private static FluidStack[] fluids(MachineRecipe recipe, VesselOperation operation, MutableRecipeFluids fluids,
                                        boolean ordinary, boolean finish, boolean preview, int batch) {
        var inputs = recipe.fluidInputs(); int[] required = new int[inputs.size()], consumed = new int[inputs.size()];
        FluidStack[] state = new FluidStack[fluids.getTanks()]; int[] available = new int[state.length];
        boolean[][] accepts = new boolean[inputs.size()][state.length];
        for (int t = 0; t < state.length; t++) { state[t] = fluids.getFluidInTank(t).copy(); available[t] = fluids.canExtractTank(t) ? state[t].getAmount() : 0; }
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i); var settings = input.settings();
            int events = Math.multiplyExact(batch, settings.periodic() ? due(settings, recipe, operation, preview) : ordinary ? 1 : 0);
            if (settings.catalyst() || events == 0 && !settings.periodic() && !operation.paid()) required[i] = Math.multiplyExact(input.ingredient().amount(), batch);
            else if (events > 0) {
                var amounts = consumption(input.ingredient().amount(), input.chance(), events,
                        operation.random(settings.periodic() ? 5000L + i * 4099L + operation.ticks() * 1000003L : 9000L + i), preview, batch);
                required[i] = amounts[0]; consumed[i] = amounts[1];
            }
            for (int t = 0; t < state.length; t++) accepts[i][t] = input.ingredient().ingredient().test(state[t]);
        }
        var products = new ArrayList<FluidStack>();
        for (int i = 0; i < recipe.fluidOutputs().size(); i++) {
            var output = recipe.fluidOutputs().get(i); int events = Math.multiplyExact(batch, output.perTick() ? due(output.settings(), recipe, operation, preview) : finish ? 1 : 0);
            int amount = consumption(output.stack().getAmount(), output.chance(), events,
                    operation.random(output.perTick() ? 6000L + i * 4099L + operation.ticks() * 1000003L : 7000L + i), preview, batch)[1];
            products.add(output.stack().copyWithAmount(amount));
        }
        FluidStack[][] result = new FluidStack[1][];
        Predicate<int[]> fits = allocation -> {
            FluidStack[] after = java.util.Arrays.stream(state).map(FluidStack::copy).toArray(FluidStack[]::new);
            for (int t = 0; t < state.length; t++) after[t].shrink(allocation[t]);
            FluidStack[] inserted;
            if (java.util.stream.IntStream.range(0, products.size()).noneMatch(i -> fluids.outputTank(i) >= 0))
                inserted = RecipeTransaction.insertFluids(products, fluids, after, null);
            else {
            inserted = after;
            for (int i = 0; i < products.size(); i++) {
                var product = products.get(i);
                if (product.isEmpty()) continue;
                int target = fluids.outputTank(i);
                if (target < 0) {
                    inserted = RecipeTransaction.insertFluids(java.util.List.of(product), fluids, inserted, null);
                    if (inserted == null) return false;
                } else {
                    if (target >= inserted.length || !fluids.canInsertTank(target, product)) return false;
                    var present = inserted[target];
                    if (!present.isEmpty() && !FluidStack.isSameFluidSameComponents(present, product)) return false;
                    long total = (long)present.getAmount() + product.getAmount();
                    if (total > fluids.getTankCapacity(target)) return false;
                    inserted[target] = product.copyWithAmount((int)total);
                }
            }
            }
            if (inserted == null) return false; result[0] = inserted; return true;
        };
        return InputAllocation.consumeForOutputs(available, required, consumed, accepts, fits) == null ? null : result[0];
    }
    private VesselTransaction() {}
}
