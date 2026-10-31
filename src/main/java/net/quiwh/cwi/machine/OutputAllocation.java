package net.quiwh.cwi.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Assigns each empty slot/tank to one product; capacities may depend on both product and slot. */
final class OutputAllocation {
    /** Necessary total-space bound; uses the fixed consumption total, never independent per-slot maxima. */
    static boolean hasAggregateSpace(int[] occupied, int[] limits, long consumed, long produced) {
        long free = consumed;
        for (int slot = 0; slot < occupied.length; slot++) free += Math.max(occupied[slot], limits[slot]) - (long) occupied[slot];
        return produced <= free;
    }

    /** Optimistic capacity of one product under a fixed removal budget, including slots that must be emptied first. */
    static boolean hasProductSpace(long needed, long free, long matchingGain, long budget,
                                   int[] unlockCosts, int[] unlockGains) {
        if (needed <= free + Math.min(budget, matchingGain)) return true;
        var frontier = new TreeMap<Long, Long>();
        frontier.put(0L, 0L);
        for (int slot = 0; slot < unlockCosts.length; slot++) {
            var next = new TreeMap<>(frontier);
            for (var entry : frontier.entrySet()) {
                long cost = entry.getKey() + unlockCosts[slot];
                if (cost > budget) continue;
                long gain = entry.getValue() + unlockGains[slot];
                if (needed <= free + gain + Math.min(budget - cost, matchingGain)) return true;
                next.merge(cost, gain, Math::max);
            }
            // A more expensive state with no additional capacity cannot improve any later choice.
            long best = -1;
            var iterator = next.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getValue() <= best) iterator.remove();
                else best = entry.getValue();
            }
            frontier = next;
        }
        return false;
    }

    static int[][] allocate(long[] required, int[][] capacities) {
        return allocate(required, capacities, null);
    }
    static int[][] allocate(long[] required, int[][] capacities, Predicate<int[][]> acceptable) {
        int slots = capacities.length == 0 ? 0 : capacities[0].length;
        List<Integer> groups = new ArrayList<>();
        for (int product = 0; product < required.length; product++) {
            if (required[product] < 0 || capacities[product].length != slots) return null;
            if (required[product] > 0) groups.add(product);
        }
        if (groups.size() > slots) return null;
        // Products with fewer destinations go first. This is only search ordering; every feasible assignment is considered.
        groups.sort(Comparator.comparingLong((Integer p) -> Arrays.stream(capacities[p]).filter(c -> c > 0).count())
                .thenComparing(Comparator.comparingLong((Integer p) -> required[p]).reversed()));
        var search = new Search(required, capacities, groups, slots, acceptable);
        return search.assign(0) ? search.result : null;
    }

    private static final class Search {
        private final long[] required;
        private final int[][] capacities, result;
        private final List<Integer> groups;
        private final BitSet occupied;
        private final Set<State> failed = new HashSet<>();
        private final Predicate<int[][]> acceptable;

        Search(long[] required, int[][] capacities, List<Integer> groups, int slots, Predicate<int[][]> acceptable) {
            this.required = required;
            this.capacities = capacities;
            this.groups = groups;
            occupied = new BitSet(slots);
            result = new int[required.length][slots];
            this.acceptable = acceptable;
        }

        private boolean assign(int index) {
            if (index == groups.size()) return acceptable == null || acceptable.test(result);
            var state = new State(index, (BitSet) occupied.clone());
            if (acceptable == null && failed.contains(state)) return false;
            // Reject shortages before exploring combinations, including shortages caused in later products.
            int minimumSlots = 0;
            for (int next = index; next < groups.size(); next++) {
                int product = groups.get(next);
                long available = 0;
                List<Integer> freeCapacities = new ArrayList<>();
                for (int slot = 0; slot < capacities[product].length; slot++)
                    if (!occupied.get(slot) && capacities[product][slot] > 0) {
                        available += capacities[product][slot];
                        freeCapacities.add(capacities[product][slot]);
                    }
                if (available < required[product]) { failed.add(state); return false; }
                freeCapacities.sort(Comparator.reverseOrder());
                long needed = required[product];
                for (int capacity : freeCapacities) {
                    minimumSlots++;
                    needed -= capacity;
                    if (needed <= 0) break;
                }
            }
            if (minimumSlots > capacities[0].length - occupied.cardinality()) { failed.add(state); return false; }
            int product = groups.get(index);
            List<Integer> candidates = new ArrayList<>();
            for (int slot = 0; slot < capacities[product].length; slot++)
                if (!occupied.get(slot) && capacities[product][slot] > 0) candidates.add(slot);
            candidates.sort(Comparator.comparingInt((Integer slot) -> capacities[product][slot]).reversed());
            if (choose(index, product, candidates, 0, required[product])) return true;
            failed.add(state);
            return false;
        }

        private boolean choose(int index, int product, List<Integer> candidates, int start, long remaining) {
            if (remaining <= 0) return assign(index + 1);
            long available = 0;
            for (int at = start; at < candidates.size(); at++) available += capacities[product][candidates.get(at)];
            if (available < remaining) return false;
            List<Integer> tried = new ArrayList<>();
            for (int at = start; at < candidates.size(); at++) {
                int slot = candidates.get(at);
                boolean equivalent = false;
                // Identical capacity columns are interchangeable, avoiding factorial searches on ordinary uniform storage.
                for (int previous : tried) {
                    boolean same = true;
                    for (int next = index; next < groups.size(); next++)
                        if (capacities[groups.get(next)][slot] != capacities[groups.get(next)][previous]) { same = false; break; }
                    if (same) { equivalent = true; break; }
                }
                if (equivalent && acceptable == null) continue;
                tried.add(slot);
                int amount = (int) Math.min(remaining, capacities[product][slot]);
                occupied.set(slot);
                result[product][slot] = amount;
                if (choose(index, product, candidates, at + 1, remaining - amount)) return true;
                result[product][slot] = 0;
                occupied.clear(slot);
            }
            return false;
        }
    }

    private record State(int product, BitSet occupied) {}
    private OutputAllocation() {}
}
