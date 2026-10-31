package net.quiwh.cwi.machine;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.Predicate;

/** Bipartite capacity matching: overlapping tags cannot double-count the same stack/tank. */
public final class InputAllocation {
    public static int[] allocate(int[] available, int[] required, boolean[][] accepts) {
        int[][] allocation = allocateDetailed(available, required, accepts);
        if (allocation == null) return null;
        int[] used = new int[available.length];
        for (int[] demand : allocation) for (int s = 0; s < used.length; s++) used[s] += demand[s];
        return used;
    }
    public static int[][] allocateDetailed(int[] available, int[] required, boolean[][] accepts) {
        int supply = available.length, demand = required.length, sink = supply + demand + 1, n = sink + 1;
        int[][] flow = new int[n][n];
        long total = 0;
        for (int s = 0; s < supply; s++) flow[0][s + 1] = available[s];
        for (int d = 0; d < demand; d++) {
            if (required[d] < 0) return null;
            total += required[d]; flow[1 + supply + d][sink] = required[d];
            for (int s = 0; s < supply; s++) if (accepts[d][s]) flow[s + 1][1 + supply + d] = Math.min(available[s], required[d]);
        }
        long allocated = 0;
        int[] previous = new int[n];
        while (allocated < total) {
            Arrays.fill(previous, -1); previous[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(0);
            while (!queue.isEmpty() && previous[sink] < 0) {
                int from = queue.remove();
                for (int to = 1; to < n; to++) if (previous[to] < 0 && flow[from][to] > 0) {
                    previous[to] = from; queue.add(to);
                }
            }
            if (previous[sink] < 0) return null;
            int amount = Integer.MAX_VALUE;
            for (int v = sink; v != 0; v = previous[v]) amount = Math.min(amount, flow[previous[v]][v]);
            for (int v = sink; v != 0; v = previous[v]) { flow[previous[v]][v] -= amount; flow[v][previous[v]] += amount; }
            allocated += amount;
        }
        int[][] assigned = new int[demand][supply];
        for (int d = 0; d < demand; d++) for (int s = 0; s < supply; s++) assigned[d][s] = flow[1 + supply + d][s + 1];
        return assigned;
    }

    /** Selects consumed units while separately reserving unconsumed ingredients and satisfying output space. */
    static int[] consumeForOutputs(int[] available, int[] required, int[] consumed, boolean[][] accepts, Predicate<int[]> fits) {
        return consumeForOutputs(available, required, consumed, accepts, fits, fits);
    }
    static int[] consumeForOutputs(int[] available, int[] required, int[] consumed, boolean[][] accepts,
                                  Predicate<int[]> fits, Predicate<int[]> optimisticFits) {
        int[] upper = available.clone();
        for (int slot = 0; slot < upper.length; slot++) {
            long canConsume = 0;
            for (int demand = 0; demand < consumed.length; demand++) if (accepts[demand][slot]) canConsume += consumed[demand];
            upper[slot] = (int) Math.min(upper[slot], canConsume);
        }
        return searchConsumption(available, required, consumed, accepts, new int[available.length], upper, fits, optimisticFits);
    }

    private static int[] searchConsumption(int[] available, int[] required, int[] consumed, boolean[][] accepts,
                                            int[] lower, int[] upper, Predicate<int[]> fits, Predicate<int[]> optimisticFits) {
        int[] candidate = consumptionFlow(available, required, consumed, accepts, lower, upper);
        if (candidate == null) return null;
        if (fits.test(candidate)) return candidate;
        // Removing more contents can only create output room. This optimistic bound need not satisfy the input demands.
        if (!optimisticFits.test(upper)) return null;
        int branch = -1;
        long range = 0;
        for (int slot = 0; slot < upper.length; slot++) if (upper[slot] > lower[slot] && upper[slot] - (long) lower[slot] > range) {
            branch = slot; range = upper[slot] - (long) lower[slot];
        }
        if (branch < 0) return null;
        int split = (int) (lower[branch] + (upper[branch] - (long) lower[branch] + 1) / 2);
        int previousLower = lower[branch], previousUpper = upper[branch];
        lower[branch] = split;
        int[] result = searchConsumption(available, required, consumed, accepts, lower, upper, fits, optimisticFits);
        lower[branch] = previousLower;
        if (result != null) return result;
        upper[branch] = split - 1;
        result = searchConsumption(available, required, consumed, accepts, lower, upper, fits, optimisticFits);
        upper[branch] = previousUpper;
        return result;
    }

    private static int[] consumptionFlow(int[] available, int[] required, int[] consumed, boolean[][] accepts, int[] lower, int[] upper) {
        int slots = available.length, demands = required.length, sink = 1 + slots * 2 + demands * 2;
        int superSource = sink + 1, superSink = sink + 2;
        long[][] residual = new long[sink + 3][sink + 3];
        long[] balance = new long[sink + 1];
        for (int slot = 0; slot < slots; slot++) {
            boundedEdge(residual, balance, 0, 1 + slot, 0, available[slot]);
            boundedEdge(residual, balance, 1 + slot, 1 + slots + slot, lower[slot], upper[slot]);
        }
        for (int demand = 0; demand < demands; demand++) {
            int consumedNode = 1 + slots * 2 + demand, reservedNode = consumedNode + demands;
            int reserve = required[demand] - consumed[demand];
            if (reserve < 0) return null;
            boundedEdge(residual, balance, consumedNode, sink, consumed[demand], consumed[demand]);
            boundedEdge(residual, balance, reservedNode, sink, reserve, reserve);
            for (int slot = 0; slot < slots; slot++) if (accepts[demand][slot]) {
                boundedEdge(residual, balance, 1 + slots + slot, consumedNode, 0, consumed[demand]);
                boundedEdge(residual, balance, 1 + slot, reservedNode, 0, reserve);
            }
        }
        residual[sink][0] = Long.MAX_VALUE / 4;
        long requiredFlow = 0;
        for (int node = 0; node <= sink; node++) {
            if (balance[node] > 0) { residual[superSource][node] = balance[node]; requiredFlow += balance[node]; }
            else residual[node][superSink] = -balance[node];
        }
        int[] previous = new int[residual.length];
        long flow = 0;
        while (flow < requiredFlow) {
            Arrays.fill(previous, -1); previous[superSource] = superSource;
            var queue = new ArrayDeque<Integer>(); queue.add(superSource);
            while (!queue.isEmpty() && previous[superSink] < 0) {
                int from = queue.remove();
                for (int to = 0; to < residual.length; to++) if (previous[to] < 0 && residual[from][to] > 0) {
                    previous[to] = from; queue.add(to);
                }
            }
            if (previous[superSink] < 0) return null;
            long amount = Long.MAX_VALUE;
            for (int node = superSink; node != superSource; node = previous[node]) amount = Math.min(amount, residual[previous[node]][node]);
            for (int node = superSink; node != superSource; node = previous[node]) {
                residual[previous[node]][node] -= amount;
                residual[node][previous[node]] += amount;
            }
            flow += amount;
        }
        int[] result = new int[slots];
        for (int slot = 0; slot < slots; slot++) result[slot] = Math.toIntExact(lower[slot] + residual[1 + slots + slot][1 + slot]);
        return result;
    }

    private static void boundedEdge(long[][] residual, long[] balance, int from, int to, long lower, long upper) {
        residual[from][to] += upper - lower;
        balance[from] -= lower; balance[to] += lower;
    }
    private InputAllocation() {}
}
