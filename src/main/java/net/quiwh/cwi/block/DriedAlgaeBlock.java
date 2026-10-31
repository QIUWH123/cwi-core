package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin algae mat with vanilla lily-pad placement and boat interactions. */
public final class DriedAlgaeBlock extends WaterlilyBlock {
    public static final MapCodec<WaterlilyBlock> CODEC = simpleCodec(DriedAlgaeBlock::new);
    private static final VoxelShape OUTLINE = Block.box(0, 0, 0, 16, 1, 16);

    public DriedAlgaeBlock(Properties properties) { super(properties); }

    @Override public MapCodec<WaterlilyBlock> codec() { return CODEC; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return OUTLINE;
    }
}
