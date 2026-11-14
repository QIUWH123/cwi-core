package net.quiwh.cwi.machine.recipe;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeInput;

/** Counted item/fluid matching with shared-slot allocation preserved. */
final class MachineRecipeMatching {
    static boolean matches(List<MachineRecipe.ItemInput> itemInputs, List<MachineRecipe.FluidInput> fluidInputs, RecipeInput input) {
        if (!fluidInputs.isEmpty() && !(input instanceof MachineRecipe.Input)) return false;
        int slots = input.size();
        long[][] flow = new long[2 + itemInputs.size() + slots][2 + itemInputs.size() + slots];
        int target = flow.length - 1;
        long required = 0;
        for (int i = 0; i < itemInputs.size(); i++) {
            MachineRecipe.ItemInput ingredient = itemInputs.get(i);
            flow[0][1 + i] = ingredient.count(); required += ingredient.count();
            for (int slot = 0; slot < slots; slot++) if (ingredient.ingredient().test(input.getItem(slot)))
                flow[1 + i][1 + itemInputs.size() + slot] = Integer.MAX_VALUE;
        }
        for (int slot = 0; slot < slots; slot++) flow[1 + itemInputs.size() + slot][target] = input.getItem(slot).getCount();
        if (maximumFlow(flow, target) != required) return false;
        if (!(input instanceof MachineRecipe.Input full)) return true;
        long[][] fluids = new long[2 + fluidInputs.size() + full.fluids().size()][2 + fluidInputs.size() + full.fluids().size()];
        target = fluids.length - 1; required = 0;
        for (int i = 0; i < fluidInputs.size(); i++) {
            MachineRecipe.FluidInput ingredient = fluidInputs.get(i);
            fluids[0][1 + i] = ingredient.ingredient().amount(); required += ingredient.ingredient().amount();
            for (int tank = 0; tank < full.fluids().size(); tank++) if (ingredient.ingredient().ingredient().test(full.fluids().get(tank)))
                fluids[1 + i][1 + fluidInputs.size() + tank] = Integer.MAX_VALUE;
        }
        for (int tank = 0; tank < full.fluids().size(); tank++) fluids[1 + fluidInputs.size() + tank][target] = full.fluids().get(tank).getAmount();
        return maximumFlow(fluids, target) == required;
    }

    private static long maximumFlow(long[][] residual, int target) {
        long flow = 0;
        int[] parent = new int[residual.length];
        while (true) {
            Arrays.fill(parent, -1); parent[0] = 0;
            var queue = new ArrayDeque<Integer>(); queue.add(0);
            while (!queue.isEmpty() && parent[target] == -1) {
                int node = queue.remove();
                for (int next = 0; next < residual.length; next++) if (parent[next] == -1 && residual[node][next] > 0) {
                    parent[next] = node; queue.add(next);
                }
            }
            if (parent[target] == -1) return flow;
            long amount = Long.MAX_VALUE;
            for (int node = target; node != 0; node = parent[node]) amount = Math.min(amount, residual[parent[node]][node]);
            for (int node = target; node != 0; node = parent[node]) { residual[parent[node]][node] -= amount; residual[node][parent[node]] += amount; }
            flow += amount;
        }
    }

    private MachineRecipeMatching() {}
}
