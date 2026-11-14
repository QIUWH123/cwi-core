package net.quiwh.cwi.machine;

import java.util.ArrayList;
import java.util.List;

/** Authored 5x5 Y pattern. Coordinates are right/up/back relative to the controller. */
public final class FurnaceStructure {
    private static final String[][] LAYERS = {
            {"00100", "02320", "13331", "02320", "00100"},
            {"00000", "02520", "13031", "02320", "00100"},
            {"00100", "02420", "14041", "02420", "00100"},
            {"00100", "02420", "14041", "02420", "00100"}
    };
    public enum Cell { AIR, BRICK, HATCH, CAST, STEEL, REINFORCED_BRICK, CONTROLLER, OTHER }
    public record Offset(int right, int up, int back) {}
    @FunctionalInterface public interface Grid { Cell get(int right, int up, int back); }
    public record Match(int height, int armor, int parallel, List<Offset> hatches) {}

    private record Test(Offset offset, int index, char predicate) {}
    private static final List<List<Test>> PATTERNS = patterns();
    private static List<List<Test>> patterns() {
        var patterns = new ArrayList<List<Test>>();
        for (int height = 8; height >= 4; height--) {
            var cells = new ArrayList<Test>();
            for (int y = 0; y < height; y++) for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) {
                char predicate = predicate(x, y, z);
                if (predicate != '0') cells.add(new Test(new Offset(x - 2, y - 1, z - 1), y * 25 + z * 5 + x, predicate));
            }
            patterns.add(List.copyOf(cells));
        }
        return List.copyOf(patterns);
    }

    public static Match scan(Grid grid) { return new Scanner().scan(grid); }

    /** Recheck every previously decisive cell on every call, including failed patterns.
     * Unchanged classifications reuse the immutable match. No delayed break detection. */
    public static final class Scanner {
        private final Cell[] observed = new Cell[200], current = new Cell[200];
        private final long[] readAt = new long[200];
        private long scan;
        private boolean initialized;
        private Match match;

        public Match scan(Grid grid) {
            scan++;
            boolean changed = !initialized;
            for (int i = 0; i < observed.length; i++) if (observed[i] != null)
                changed |= read(grid, i) != observed[i];
            if (!changed) return match;
            initialized = true;
            java.util.Arrays.fill(observed, null);
            match = null;
            for (int pattern = 0; pattern < PATTERNS.size(); pattern++) {
                int armor = 0, bricks = 0;
                boolean valid = true;
                for (var test : PATTERNS.get(pattern)) {
                    Cell cell = read(grid, test.index());
                    observed[test.index()] = cell;
                    boolean reinforcement = cell == Cell.CAST || cell == Cell.STEEL;
                    boolean accepts = switch (test.predicate()) {
                        case '1' -> reinforcement || cell == Cell.AIR;
                        case '2' -> reinforcement || cell == Cell.REINFORCED_BRICK;
                        case '3' -> cell == Cell.BRICK;
                        case '4' -> cell == Cell.BRICK || cell == Cell.HATCH;
                        case '5' -> cell == Cell.CONTROLLER;
                        default -> false;
                    };
                    if (!accepts) { valid = false; break; }
                    if (cell == Cell.BRICK || cell == Cell.HATCH) bricks++;
                    if (cell == Cell.CAST) armor++;
                    if (cell == Cell.STEEL) armor += 2;
                }
                if (!valid) continue;
                List<Offset> hatches = PATTERNS.get(pattern).stream()
                        .filter(test -> current[test.index()] == Cell.HATCH).map(Test::offset).toList();
                match = new Match(8 - pattern, armor, 2 * (bricks / 4), hatches);
                break;
            }
            return match;
        }

        private Cell read(Grid grid, int index) {
            if (readAt[index] != scan) {
                current[index] = grid.get(index % 5 - 2, index / 25 - 1, index / 5 % 5 - 1);
                readAt[index] = scan;
            }
            return current[index];
        }
    }

    /** Useful to preview the native structure without depending on the former editor. */
    public static char predicate(int x, int y, int z) { return LAYERS[Math.min(y, 3)][z].charAt(x); }
    private FurnaceStructure() {}
}
