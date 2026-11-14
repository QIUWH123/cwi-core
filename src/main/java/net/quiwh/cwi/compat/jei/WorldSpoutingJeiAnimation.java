package net.quiwh.cwi.compat.jei;

import static com.simibubi.create.compat.jei.category.animations.AnimatedKinetics.DEFAULT_LIGHTING;
import static com.simibubi.create.compat.jei.category.animations.AnimatedKinetics.defaultBlockElement;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;

/** Create 6's AnimatedSpout cycle, using the authored camera/scale and a world block instead of its depot. */
final class WorldSpoutingJeiAnimation {
    private static final int SCALE = 24;

    void draw(GuiGraphics graphics, FluidStack fluid) {
        var pose = graphics.pose();
        defaultBlockElement(AllBlocks.SPOUT.getDefaultState()).scale(SCALE).render(graphics);
        float cycle = AnimationTickHolder.getRenderTime() % 30;
        float squeeze = cycle < 20 ? Mth.sin((float) (cycle / 20 * Math.PI)) * 20 : 0;
        float delta = -3 * squeeze / 32;
        pose.pushPose();
        defaultBlockElement(AllPartialModels.SPOUT_TOP).scale(SCALE).render(graphics);
        pose.translate(0, delta, 0);
        defaultBlockElement(AllPartialModels.SPOUT_MIDDLE).scale(SCALE).render(graphics);
        pose.translate(0, delta, 0);
        defaultBlockElement(AllPartialModels.SPOUT_BOTTOM).scale(SCALE).render(graphics);
        pose.popPose();

        if (fluid.isEmpty()) return;
        DEFAULT_LIGHTING.applyLighting();
        pose.pushPose();
        UIRenderHelper.flipForGuiRender(pose);
        // AnimatedSpout uses scale 20 and fluid units 16. Resize both together for the pack's scale 24.
        float fluidScale = 16f * SCALE / 20;
        pose.scale(fluidScale, fluidScale, fluidScale);
        fluidBox(graphics, pose, fluid, 3 / 16f, 3 / 16f, 3 / 16f, 17 / 16f, 17 / 16f, 17 / 16f);
        pose.popPose();

        pose.pushPose();
        pose.translate(SCALE / 2f, SCALE * 1.5f, SCALE / 2f);
        UIRenderHelper.flipForGuiRender(pose);
        pose.scale(fluidScale, fluidScale, fluidScale);
        pose.translate(-.5f, 0, -.5f);
        float width = squeeze / 128;
        // No degenerate fluid box during Create's ten-tick resting phase.
        if (width > 0) fluidBox(graphics, pose, fluid, .5f - width / 2, 0, .5f - width / 2,
                .5f + width / 2, 2, .5f + width / 2);
        pose.popPose();
        graphics.flush();
        Lighting.setupFor3DItems();
    }

    private static void fluidBox(GuiGraphics graphics, PoseStack pose, FluidStack fluid,
                                 float x1, float y1, float z1, float x2, float y2, float z2) {
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, x1, y1, z1, x2, y2, z2,
                graphics.bufferSource(), pose, LightTexture.FULL_BRIGHT, false, true);
    }
}
