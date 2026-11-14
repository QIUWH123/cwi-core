package net.quiwh.cwi.machine;

import java.util.Arrays;
import java.util.Random;

/** Independent exhaustive destination-owner oracle for small constrained item/tank layouts. */
public final class OutputAllocationContractTest {
    public static void main(String[] args) {
        verify();
        System.out.println("OutputAllocationContractTest: 5,000 exhaustive-oracle layouts and 16-slot uniform bounds PASS");
    }

    public static void verify() {
        var random = new Random(6126024);
        for (int trial = 0; trial < 5000; trial++) {
            int products = 1 + random.nextInt(4), slots = random.nextInt(7);
            long[] required = new long[products];
            int[][] capacities = new int[products][slots];
            for (int product = 0; product < products; product++) {
                required[product] = random.nextInt(10);
                for (int slot = 0; slot < slots; slot++) capacities[product][slot] = random.nextInt(5);
            }
            boolean[] preferred = new boolean[slots];
            for(int slot=0;slot<slots;slot++)preferred[slot]=random.nextBoolean();
            int[][] plan = trial % 2 == 0 ? OutputAllocation.allocate(required, capacities)
                    : OutputAllocation.allocate(required, capacities, preferred, null);
            boolean expected = bruteForce(required, capacities, 0, new long[products]);
            if ((plan != null) != expected) throw new AssertionError("Oracle mismatch " + Arrays.toString(required) + Arrays.deepToString(capacities));
            if (plan == null) continue;
            for (int slot = 0; slot < slots; slot++) {
                int owners = 0;
                for (int product = 0; product < products; product++) {
                    if (plan[product][slot] < 0 || plan[product][slot] > capacities[product][slot]) throw new AssertionError("Invalid slot capacity");
                    if (plan[product][slot] > 0) owners++;
                }
                if (owners > 1) throw new AssertionError("Products share a destination");
            }
            for (int product = 0; product < products; product++)
                if (Arrays.stream(plan[product]).asLongStream().sum() != required[product]) throw new AssertionError("Incorrect product amount");
        }
        var merged=OutputAllocation.allocate(new long[]{2},new int[][]{{2,64}},new boolean[]{true,false},null);
        if(merged==null || merged[0][0]!=2 || merged[0][1]!=0)throw new AssertionError("Existing stack/tank was bypassed for an empty destination");
        var fallback=OutputAllocation.allocate(new long[]{2},new int[][]{{2,64}},new boolean[]{true,false},p->p[0][0]==0);
        if(fallback==null || fallback[0][1]!=2)throw new AssertionError("Merge preference bypassed a future-state constraint");
        long[] required = new long[16]; Arrays.fill(required, 1);
        int[][] capacities = new int[16][16]; for (int[] row : capacities) Arrays.fill(row, 64);
        if (OutputAllocation.allocate(required, capacities) == null) throw new AssertionError("Uniform 16-product layout refused");
        required[0] = 65;
        if (OutputAllocation.allocate(required, capacities) != null) throw new AssertionError("17 required destinations fit 16 slots");
        required[0] = 1;
        capacities[0][0] = Integer.MAX_VALUE;
        required[0] = Integer.MAX_VALUE;
        if (OutputAllocation.allocate(required, capacities) == null) throw new AssertionError("Long total capacity overflow");
        int[] full = {64, 64, 64, 64, 64, 64};
        if (OutputAllocation.hasAggregateSpace(full, full, 128, 129)) throw new AssertionError("Independent slot maxima exceeded the fixed consumption budget");
        if (!OutputAllocation.hasAggregateSpace(full, full, 128, 128)) throw new AssertionError("Exact saturated replacement refused");
        if (OutputAllocation.hasProductSpace(65, 0, 0, 65, full, full)) throw new AssertionError("Indivisible destination costs ignored");
        if (!OutputAllocation.hasProductSpace(128, 0, 0, 128, full, full)) throw new AssertionError("Two freeable destinations refused");
        if (!OutputAllocation.hasAggregateSpace(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE},
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 2L * Integer.MAX_VALUE, 2L * Integer.MAX_VALUE))
            throw new AssertionError("Aggregate capacity overflow");
        for (int trial = 0; trial < 5000; trial++) {
            int slots = random.nextInt(7);
            int[] costs = new int[slots], gains = new int[slots];
            for (int slot = 0; slot < slots; slot++) { costs[slot] = 1 + random.nextInt(5); gains[slot] = random.nextInt(6); }
            long free = random.nextInt(6), matching = random.nextInt(6), budget = random.nextInt(16), needed = random.nextInt(40);
            long maximum = free + Math.min(budget, matching);
            for (int mask = 0; mask < (1 << slots); mask++) {
                long cost = 0, gain = 0;
                for (int slot = 0; slot < slots; slot++) if ((mask & (1 << slot)) != 0) { cost += costs[slot]; gain += gains[slot]; }
                if (cost <= budget) maximum = Math.max(maximum, free + gain + Math.min(budget - cost, matching));
            }
            if (OutputAllocation.hasProductSpace(needed, free, matching, budget, costs, gains) != (needed <= maximum))
                throw new AssertionError("Product capacity budget disagrees with subset oracle");
        }
    }

    private static boolean bruteForce(long[] required, int[][] capacities, int slot, long[] totals) {
        boolean complete = true;
        for (int product = 0; product < required.length; product++) if (totals[product] < required[product]) { complete = false; break; }
        if (complete) return true;
        if (slot == capacities[0].length) return false;
        if (bruteForce(required, capacities, slot + 1, totals)) return true;
        for (int product = 0; product < required.length; product++) if (totals[product] < required[product] && capacities[product][slot] > 0) {
            totals[product] += capacities[product][slot];
            boolean accepted = bruteForce(required, capacities, slot + 1, totals);
            totals[product] -= capacities[product][slot];
            if (accepted) return true;
        }
        return false;
    }
}
