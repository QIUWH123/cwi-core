package net.quiwh.cwi.compat.jei;

import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.Blocks;
import net.quiwh.cwi.machine.FurnaceMaterials;
import net.quiwh.cwi.machine.FurnaceStructure;
import net.quiwh.cwi.machine.MachineBlocks;

/** Four-layer furnace cutaway, rendered from the same structure predicates and models as gameplay. */
final class FurnaceJeiAnimation extends AnimatedKinetics {
    @Override public void draw(GuiGraphics graphics, int x, int y) {
        var pose = graphics.pose(); pose.pushPose(); pose.translate(x, y, 100);
        for (int up = 0; up < 4; up++) for (int back = 4; back >= 0; back--) for (int right = 0; right < 5; right++) {
            char cell = FurnaceStructure.predicate(right, up, back);
            // Omit the nearest quarter to expose the inside; retain the controller and one hatch.
            if (cell == '0' || up > 0 && right > 2 && back < 2) continue;
            var state = switch (cell) {
                case '1', '2' -> FurnaceMaterials.CAST_REINFORCEMENT.get().defaultBlockState();
                case '3', '4' -> FurnaceMaterials.FIREPROOF_BRICKS.get().defaultBlockState();
                case '5' -> MachineBlocks.BLAST_FURNACE.get().defaultBlockState();
                default -> Blocks.AIR.defaultBlockState();
            };
            if (up == 2 && right == 2 && back == 1) state = MachineBlocks.FURNACE_HATCH.get().defaultBlockState();
            // Project integer block offsets through the same camera as each cube. atLocal is in block units.
            double diagonal = Math.sqrt(.5), sin = Math.sin(Math.PI / 8), cos = Math.cos(Math.PI / 8);
            blockElement(state).rotate(22.5, 45, 0).atLocal((right + back - 4) * diagonal,
                    (back - right) * sin * diagonal - up * cos, (right - back) * cos * diagonal - up * sin)
                    .scale(8).render(graphics);
        }
        // Native fire model animates through Minecraft's block atlas, without a new texture.
        blockElement(Blocks.FIRE.defaultBlockState()).rotate(22.5, 45, 0).atLocal(0, 1, 1).scale(9).render(graphics);
        pose.popPose();
    }
}
