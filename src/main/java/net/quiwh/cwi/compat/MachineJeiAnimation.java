package net.quiwh.cwi.compat;

import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.quiwh.cwi.machine.MachineBlock;
import net.quiwh.cwi.machine.client.MachineClient;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

/** Uses the same baked native block/rotor models as the in-world renderers. */
final class MachineJeiAnimation extends AnimatedKinetics {
    private final MachineRecipes.Entry entry;

    MachineJeiAnimation(MachineRecipes.Entry entry) { this.entry = entry; }

    @Override public void draw(GuiGraphics graphics, int x, int y) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-12.5f));
        pose.mulPose(Axis.YP.rotationDegrees(22.5f));
        var state = BuiltInRegistries.BLOCK.get(entry.machineId()).defaultBlockState();
        blockElement(state).rotateBlock(0, 180, 0).atLocal(0, 0, 2).scale(24).render(graphics);
        if (entry == MachineRecipes.MIXING)
            blockElement(MachineClient.mixerHead()).rotateBlock(0, getCurrentAngle() * 8, 0)
                    .atLocal(0, 0, 2).scale(24).render(graphics);
        pose.popPose();
    }
}
