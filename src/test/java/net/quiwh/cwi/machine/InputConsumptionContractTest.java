package net.quiwh.cwi.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/** Independent exhaustive oracle: each demand consumes and reserves disjoint supply units. */
public final class InputConsumptionContractTest {
    private static final int RANDOM_LAYOUTS = 5000;

    public static void main(String[] args) {
        verify();
        System.out.println("InputConsumptionContractTest: 5,000 exhaustive-oracle layouts, "
                + "15,000 predicate checks and 3 integer-boundary checks PASS");
    }

    public static void verify() {
        Random random = new Random(0x43574920261025L);
        for (int trial = 0; trial < RANDOM_LAYOUTS; trial++) {
            int slots = random.nextInt(5), demands = random.nextInt(5);
            int[] available = new int[slots], required = new int[demands], consumed = new int[demands];
            boolean[][] accepts = new boolean[demands][slots];
            for (int slot = 0; slot < slots; slot++) available[slot] = random.nextInt(4);
            for (int demand = 0; demand < demands; demand++) {
                required[demand] = random.nextInt(5);
                consumed[demand] = random.nextInt(required[demand] + 1);
                for (int slot = 0; slot < slots; slot++) accepts[demand][slot] = random.nextBoolean();
            }

            Map<String, int[]> feasible = enumerate(available, required, consumed, accepts);
            check(trial, available, required, consumed, accepts, feasible, vector -> true, false);

            int[] target = new int[slots];
            if (!feasible.isEmpty() && random.nextBoolean()) {
                List<int[]> vectors = new ArrayList<>(feasible.values());
                target = vectors.get(random.nextInt(vectors.size())).clone();
            } else {
                for (int slot = 0; slot < slots; slot++) target[slot] = random.nextInt(available[slot] + 1);
            }
            int[] exactTarget = target;
            check(trial, available, required, consumed, accepts, feasible,
                    vector -> Arrays.equals(vector, exactTarget), false);

            int[] threshold = new int[slots];
            for (int slot = 0; slot < slots; slot++) threshold[slot] = random.nextInt(available[slot] + 1);
            check(trial, available, required, consumed, accepts, feasible, vector -> {
                for (int slot = 0; slot < vector.length; slot++) if (vector[slot] < threshold[slot]) return false;
                return true;
            }, true);
        }
        verifyIntegerBoundaries();
    }

    private static void check(int trial, int[] available, int[] required, int[] consumed, boolean[][] accepts,
                              Map<String, int[]> feasible, Predicate<int[]> fits, boolean monotone) {
        boolean expected = feasible.values().stream().anyMatch(fits);
        int[] result = monotone
                ? InputAllocation.consumeForOutputs(available, required, consumed, accepts, fits)
                : InputAllocation.consumeForOutputs(available, required, consumed, accepts, fits, vector -> true);
        String context = "trial=" + trial + " available=" + Arrays.toString(available)
                + " required=" + Arrays.toString(required) + " consumed=" + Arrays.toString(consumed)
                + " accepts=" + Arrays.deepToString(accepts) + " result=" + Arrays.toString(result);
        require((result != null) == expected, "Predicate feasibility mismatch: " + context);
        if (result != null) {
            require(feasible.containsKey(Arrays.toString(result)), "Returned vector has no disjoint allocation: " + context);
            require(fits.test(result), "Returned vector fails output predicate: " + context);
        }
    }

    private static Map<String, int[]> enumerate(int[] available, int[] required, int[] consumed, boolean[][] accepts) {
        Map<String, int[]> feasible = new LinkedHashMap<>();
        assignDemand(0, available.clone(), new int[available.length], required, consumed, accepts, feasible);
        return feasible;
    }

    private static void assignDemand(int demand, int[] remaining, int[] totalConsumed, int[] required,
                                     int[] consumed, boolean[][] accepts, Map<String, int[]> feasible) {
        if (demand == required.length) {
            feasible.putIfAbsent(Arrays.toString(totalConsumed), totalConsumed.clone());
            return;
        }
        // Enumerate consumed units first, then separately reserve this demand's catalyst units.
        assignPart(demand, 0, consumed[demand], true, remaining, totalConsumed,
                required, consumed, accepts, feasible);
    }

    private static void assignPart(int demand, int slot, int needed, boolean consuming, int[] remaining,
                                   int[] totalConsumed, int[] required, int[] consumed, boolean[][] accepts,
                                   Map<String, int[]> feasible) {
        if (slot == remaining.length) {
            if (needed != 0) return;
            if (consuming) {
                assignPart(demand, 0, required[demand] - consumed[demand], false, remaining, totalConsumed,
                        required, consumed, accepts, feasible);
            } else {
                assignDemand(demand + 1, remaining, totalConsumed, required, consumed, accepts, feasible);
            }
            return;
        }
        int limit = accepts[demand][slot] ? Math.min(remaining[slot], needed) : 0;
        for (int amount = 0; amount <= limit; amount++) {
            remaining[slot] -= amount;
            if (consuming) totalConsumed[slot] += amount;
            assignPart(demand, slot + 1, needed - amount, consuming, remaining, totalConsumed,
                    required, consumed, accepts, feasible);
            if (consuming) totalConsumed[slot] -= amount;
            remaining[slot] += amount;
        }
    }

    private static void verifyIntegerBoundaries() {
        int max = Integer.MAX_VALUE;
        int[] result = InputAllocation.consumeForOutputs(new int[]{max, max}, new int[]{max, max},
                new int[]{max, max}, new boolean[][]{{true, false}, {false, true}}, vector -> true);
        require(Arrays.equals(result, new int[]{max, max}), "Total consumption above int maximum overflowed");

        result = InputAllocation.consumeForOutputs(new int[]{max, max}, new int[]{max, max},
                new int[]{max - 1, 1}, new boolean[][]{{true, true}, {true, false}}, vector -> true);
        require(Arrays.equals(result, new int[]{1, max - 1}), "Large catalyst reservation shared consumed supply");

        result = InputAllocation.consumeForOutputs(new int[]{max, max}, new int[]{max},
                new int[]{max}, new boolean[][]{{true, true}}, vector -> vector[1] == max);
        require(Arrays.equals(result, new int[]{0, max}), "Large output-space search overflowed or missed feasible consumption");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
