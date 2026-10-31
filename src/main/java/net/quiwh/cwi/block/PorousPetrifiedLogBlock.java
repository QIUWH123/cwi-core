package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public final class PorousPetrifiedLogBlock extends RotatedPillarBlock {
    public PorousPetrifiedLogBlock(Properties properties) { super(properties); }
    @Override public MapCodec<? extends RotatedPillarBlock> codec() { return simpleCodec(PorousPetrifiedLogBlock::new); }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(17) != 0) return;
        Direction direction = Direction.getRandom(random);
        var target = pos.relative(direction);
        var current = level.getBlockState(target);
        if (direction != Direction.UP && BuddingAmethystBlock.canClusterGrowAtState(current))
            level.setBlockAndUpdate(target, PetrifiedWood.get("amber_cluster").defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, direction).setValue(AmethystClusterBlock.WATERLOGGED, current.getFluidState().getType() == Fluids.WATER));
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) return;
        var d = Direction.getRandom(random);
        var neighborPos = pos.relative(d);
        if (d == Direction.UP || (state.canOcclude() && level.getBlockState(neighborPos).isFaceSturdy(level, neighborPos, d.getOpposite()))) return;
        level.addParticle(PetrifiedWood.DRIPPING_AMBER.get(),
                pos.getX() + (d.getStepX() == 0 ? random.nextDouble() : .5 + d.getStepX() * .6),
                pos.getY() + (d.getStepY() == 0 ? random.nextDouble() : .5 + d.getStepY() * .6),
                pos.getZ() + (d.getStepZ() == 0 ? random.nextDouble() : .5 + d.getStepZ() * .6), 0, 0, 0);
    }
}
