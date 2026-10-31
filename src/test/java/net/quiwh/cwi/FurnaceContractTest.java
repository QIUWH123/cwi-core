package net.quiwh.cwi;

import net.quiwh.cwi.machine.FurnaceHeat;
import net.quiwh.cwi.machine.FurnaceStructure;
import net.quiwh.cwi.machine.FurnaceStructure.Cell;

public final class FurnaceContractTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int height = 4; height <= 8; height++) for (Cell armor : new Cell[]{Cell.CAST, Cell.STEEL}) {
            final int h = height;
            var match = FurnaceStructure.scan((r, u, b) -> blueprint(r, u, b, h, armor));
            require(match != null && match.height() == h, "Authored furnace height " + h);
            require(match.parallel() == h * 2, "Brick parallel count");
            require(match.armor() == (8 * h - 1) * (armor == Cell.STEEL ? 2 : 1), "Armor count excludes wildcards");
            checks += 3;
        }
        require(FurnaceStructure.scan((r,u,b) -> Cell.STEEL) == null, "Arbitrary solid mass cannot form"); checks++;
        require(FurnaceStructure.scan((r,u,b) -> r == 0 && u == -1 && b == 0 ? Cell.AIR : blueprint(r,u,b,4,Cell.CAST)) == null,
                "Required floor brick cannot be absent"); checks++;
        var hatch = FurnaceStructure.scan((r,u,b) -> r == 0 && u == 1 && b == 0 ? Cell.HATCH : blueprint(r,u,b,4,Cell.CAST));
        require(hatch != null && hatch.hatches().size() == 1 && hatch.parallel() == 8, "Hatch substitutes for a permitted brick"); checks++;
        require(FurnaceHeat.target(15,31) < 1900 && FurnaceHeat.target(15,33) > 1900, "Minimum superheat armor"); checks++;
        require(FurnaceHeat.target(10,126) < 1900 && FurnaceHeat.target(11,126) > 1900, "Minimum superheat burners"); checks++;
        require(FurnaceHeat.stage(FurnaceHeat.approach(1199, 15, 0)) == 2, "Snap updates stage at 1200"); checks++;
        require(FurnaceHeat.durationFactor(FurnaceHeat.approach(1199,15,0)) == .625, "Snap updates speed"); checks++;
        require(FurnaceHeat.stage(299.99) == 0 && FurnaceHeat.stage(300) == 1 && FurnaceHeat.stage(1900) == 3, "Heat boundaries"); checks++;
        require(FurnaceHeat.durationFactor(2400) == .25 && FurnaceHeat.sanitize(Double.NaN) == 0, "Persisted heat bounded"); checks++;
        System.out.println("Furnace contracts: " + checks + " checks passed");
    }

    private static Cell blueprint(int r, int u, int b, int height, Cell armor) {
        int x = r + 2, y = u + 1, z = b + 1;
        if (x < 0 || x >= 5 || y < 0 || y >= height || z < 0 || z >= 5) return Cell.AIR;
        return switch (FurnaceStructure.predicate(x, y, z)) {
            case '1','2' -> armor;
            case '3','4' -> Cell.BRICK;
            case '5' -> Cell.CONTROLLER;
            default -> Cell.STEEL; // Extra wildcard blocks must not inflate armor.
        };
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
