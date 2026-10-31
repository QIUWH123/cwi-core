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

    public static Match scan(Grid grid) {
        for (int height = 8; height >= 4; height--) {
            int armor = 0, bricks = 0;
            boolean valid = true;
            List<Offset> hatches = new ArrayList<>();
            for (int y = 0; valid && y < height; y++) {
                String[] layer = LAYERS[Math.min(y, 3)];
                for (int z = 0; valid && z < 5; z++) for (int x = 0; x < 5; x++) {
                    char predicate = layer[z].charAt(x);
                    if (predicate == '0') continue; // Wildcards neither validate nor count armor.
                    Cell cell = grid.get(x - 2, y - 1, z - 1);
                    boolean reinforcement = cell == Cell.CAST || cell == Cell.STEEL;
                    boolean accepts = switch (predicate) {
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
                    if (cell == Cell.HATCH) hatches.add(new Offset(x - 2, y - 1, z - 1));
                }
            }
            if (valid) return new Match(height, armor, 2 * (bricks / 4), List.copyOf(hatches));
        }
        return null;
    }

    /** Useful to preview the native structure without depending on the former editor. */
    public static char predicate(int x, int y, int z) { return LAYERS[Math.min(y, 3)][z].charAt(x); }
    private FurnaceStructure() {}
}
