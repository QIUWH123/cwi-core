package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

public final class PetrifiedRootsBlock {
    private static final VoxelShape SHAPE = Block.box(2, 3, 2, 14, 16, 14);
    public static final class Head extends GrowingPlantHeadBlock {
        public Head(Properties properties) { super(properties, Direction.DOWN, SHAPE, false, .1); }
        @Override protected MapCodec<? extends GrowingPlantHeadBlock> codec() { return simpleCodec(Head::new); }
        @Override protected boolean isRandomlyTicking(BlockState state) { return false; }
        @Override protected int getBlocksToGrowWhenBonemealed(RandomSource random) { return NetherVines.getBlocksToGrowWhenBonemealed(random); }
        @Override protected boolean canGrowInto(BlockState state) { return state.isAir(); }
        @Override protected Block getBodyBlock() { return PetrifiedWood.get("petrified_roots_plant"); }
        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            var offset = state.getOffset(level, pos); return SHAPE.move(offset.x, 0, offset.z);
        }
    }
    public static final class Body extends GrowingPlantBodyBlock {
        public Body(Properties properties) { super(properties, Direction.DOWN, SHAPE, false); }
        @Override protected MapCodec<? extends GrowingPlantBodyBlock> codec() { return simpleCodec(Body::new); }
        @Override protected GrowingPlantHeadBlock getHeadBlock() { return (GrowingPlantHeadBlock) PetrifiedWood.get("petrified_roots"); }
        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            var offset = state.getOffset(level, pos); return SHAPE.move(offset.x, 0, offset.z);
        }
    }
    private PetrifiedRootsBlock() {}
}
