package net.quiwh.cwi.machine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.quiwh.cwi.machine.BlastFurnaceBlockEntity;
import net.quiwh.cwi.machine.IncubatorBlockEntity;
import net.quiwh.cwi.machine.MachineBlock;
import net.quiwh.cwi.machine.MachineBlockEntity;
import net.quiwh.cwi.machine.MixingVesselBlockEntity;
import net.quiwh.cwi.machine.SolarEvaporatorBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/** Native Create rotation plus the original machine inventory/fluid display transforms. */
final class MachineRenderer<T extends KineticBlockEntity> extends KineticBlockEntityRenderer<T> {
    private static final FluidRenderHelper<FluidStack> FLUIDS = new FluidRenderHelper<>();
    private static final double[][] PLATES = {
            {.325, .55, .675}, {.675, .55, .675}, {.325, .55, .325}, {.675, .55, .325},
            {.325, .75, .675}, {.675, .75, .675}, {.325, .75, .325}, {.675, .75, .325}};
    private static final double[][] SOLAR_ITEMS = {{.35, .15, .45}, {.6, .15, .7}, {.65, .15, .35}};
    private final @Nullable PartialModel rotating;
    private final @Nullable Direction orientation;

    MachineRenderer(BlockEntityRendererProvider.Context context) { this(context, null, null); }
    MachineRenderer(BlockEntityRendererProvider.Context context, @Nullable PartialModel rotating, @Nullable Direction orientation) {
        super(context);
        this.rotating = rotating;
        this.orientation = orientation;
    }

    @Override protected void renderSafe(T entity, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (rotating != null) {
            Direction facing = orientation == null ? entity.getBlockState().getValue(MachineBlock.FACING) : orientation;
            var model = CachedBuffers.partialFacingVertical(rotating, entity.getBlockState(), facing);
            for (var layer : rotating.get().getRenderTypes(entity.getBlockState(), entity.getLevel().random, ModelData.EMPTY)) {
                // renderInto resets the buffer, so apply the rotation and world light for each layer.
                renderRotatingBuffer(entity, model, pose, buffers.getBuffer(layer), light);
            }
        }
        if (!(entity instanceof MachineBlockEntity storage)) return;
        if (storage instanceof SolarEvaporatorBlockEntity) {
            // Solar fluid is fixed to the tray; inventory follows the machine's front.
            fluid(storage, 0, pose, buffers, light, .125f, .075f, .125f, .875f, .3f, .875f);
        }
        pose.pushPose();
        orient(pose, entity.getBlockState().getValue(MachineBlock.FACING));
        if (storage instanceof IncubatorBlockEntity) {
            for (int slot = 0; slot < PLATES.length; slot++) {
                double[] at = PLATES[slot];
                item(storage, slot, pose, buffers, partialTicks, light, overlay, at[0], at[1], at[2], .3f, 90, 0, 0, .1f, false);
            }
        } else if (storage instanceof MixingVesselBlockEntity) {
            for (int slot = 0; slot < 6; slot++)
                item(storage, slot, pose, buffers, partialTicks, light, overlay, .5, .4 + slot * .002, .5, .5f, 90, 0, 45, 0, true);
            // Keep the original reservoir bounds, giving each tank a visible strip instead of coincident faces.
            int tanks = storage.fluids().getTanks();
            for (int tank = 0; tank < tanks; tank++) {
                float left = .1f + .8f * tank / tanks;
                float right = .1f + .8f * (tank + 1) / tanks;
                fluid(storage, tank, pose, buffers, light, left, .25f, .1f, right, .5f, .9f);
            }
        } else if (storage instanceof SolarEvaporatorBlockEntity) {
            for (int slot = 0; slot < SOLAR_ITEMS.length; slot++) {
                double[] at = SOLAR_ITEMS[slot];
                item(storage, slot, pose, buffers, partialTicks, light, overlay, at[0], at[1], at[2], .33f, 90, 25, 0, 0, true);
            }
        } else if (storage instanceof BlastFurnaceBlockEntity) {
            // The original output renderer occupies the block immediately in front of the north face.
            fluid(storage, 4, pose, buffers, light, 0, 0, -1, 1, 1, 0);
        }
        pose.popPose();
    }

    private static void orient(PoseStack pose, Direction facing) {
        pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-.5, -.5, -.5);
    }

    private static void item(MachineBlockEntity entity, int slot, PoseStack pose, MultiBufferSource buffers,
                             float partialTicks, int light, int overlay, double x, double y, double z, float scale,
                             float rx, float ry, float rz, float spin, boolean renderStack) {
        ItemStack stack = entity.getItem(slot);
        if (stack.isEmpty() || entity.getLevel() == null) return;
        var renderer = Minecraft.getInstance().getItemRenderer();
        var model = renderer.getModel(stack, entity.getLevel(), null, slot);
        int copies = renderStack ? stack.getCount() > 16 ? 3 : stack.getCount() > 1 ? 2 : 1 : 1;
        pose.pushPose();
        pose.translate(x, y, z);
        if (spin != 0) pose.mulPose(Axis.YP.rotation((entity.getLevel().getGameTime() + partialTicks) * spin * (float) (Math.PI * 2 / 80)));
        pose.mulPose(new Quaternionf().rotationXYZ((float) Math.toRadians(rx), (float) Math.toRadians(ry), (float) Math.toRadians(rz)));
        pose.scale(scale, scale, scale);
        for (int copy = 0; copy < copies; copy++) {
            pose.pushPose();
            if (model.isGui3d()) {
                if (copy != 0) pose.translate((copy == 1 ? -.08 : .08), copy * .025, copy * .035);
            } else pose.translate(0, 0, copy * .1 - (copies - 1) * .05);
            renderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), slot);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void fluid(MachineBlockEntity entity, int tank, PoseStack pose, MultiBufferSource buffers, int light,
                              float x1, float y1, float z1, float x2, float height, float z2) {
        FluidStack fluid = entity.getFluid(tank);
        if (fluid.isEmpty()) return;
        float fraction = Math.clamp((float) fluid.getAmount() / entity.fluids().getTankCapacity(tank), 0, 1);
        if (fraction > 0) FLUIDS.renderFluidBox(fluid, x1, y1, z1, x2, y1 + height * fraction, z2, buffers, pose, light, false, false);
    }
}
