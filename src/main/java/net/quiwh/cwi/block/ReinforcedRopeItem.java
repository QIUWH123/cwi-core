package net.quiwh.cwi.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;

public final class ReinforcedRopeItem extends BlockItem {
    public ReinforcedRopeItem(Block block, Properties properties) { super(block, properties); }
    @Override public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        var face = context.getClickedFace();
        if (face.getAxis() == Direction.Axis.Y) return super.updatePlacementContext(context);
        var level = context.getLevel();
        var pos = context.getClickedPos().relative(face.getOpposite()).below();
        // Bound the scan to the world, including a rope ending at minimum build height.
        while (!level.isOutsideBuildHeight(pos)) {
            var state = level.getBlockState(pos);
            if (state.canBeReplaced() && (state.getFluidState().isEmpty() || state.getFluidState().getType() == Fluids.WATER))
                return BlockPlaceContext.at(context, pos, Direction.DOWN);
            if (!state.is(getBlock())) break;
            pos = pos.below();
        }
        return super.updatePlacementContext(context);
    }
}
