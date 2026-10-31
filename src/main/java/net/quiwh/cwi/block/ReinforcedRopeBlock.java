package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;

public final class ReinforcedRopeBlock extends Block implements SimpleWaterloggedBlock {
    public enum Part implements StringRepresentable {
        TOP, MIDDLE, BOTTOM;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape BOTTOM = Block.box(6, 8, 6, 10, 16, 10);
    private static final VoxelShape MIDDLE = Block.box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape TOP = Shapes.or(Block.box(6, 0, 6, 10, 15, 10),
            Block.box(7, 12, 3, 9, 16, 5), Block.box(7, 12, 11, 9, 16, 13), Block.box(7, 12, 5, 9, 14, 11));
    public ReinforcedRopeBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(PART, Part.MIDDLE).setValue(WATERLOGGED, false)); }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(ReinforcedRopeBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART, WATERLOGGED); }
    @Override public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) { return true; }
    @Override public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return true; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN) || level.getBlockState(pos.above()).is(this);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER), context.getLevel(), context.getClickedPos());
    }
    private BlockState connected(BlockState state, LevelReader level, BlockPos pos) {
        var above = level.getBlockState(pos.above());
        Part part = !level.getBlockState(pos.below()).is(this) ? Part.BOTTOM
                : !above.is(this) && above.isFaceSturdy(level, pos.above(), Direction.DOWN) ? Part.TOP : Part.MIDDLE;
        return state.setValue(PART, part);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state.canSurvive(level, pos) ? connected(state, level, pos) : Blocks.AIR.defaultBlockState();
    }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override public Optional<SoundEvent> getPickupSound() { return Optional.empty(); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) { case TOP -> TOP; case BOTTOM -> BOTTOM; default -> MIDDLE; };
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(7.5, state.getValue(PART) == Part.BOTTOM ? 8 : 0, 7.5, 8.5, 16, 8.5);
    }
}
