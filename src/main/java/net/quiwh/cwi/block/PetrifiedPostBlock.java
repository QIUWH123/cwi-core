package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public final class PetrifiedPostBlock extends RotatedPillarBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty[] CHAINED = { BooleanProperty.create("chain_down"), BooleanProperty.create("chain_up"),
            BooleanProperty.create("chain_north"), BooleanProperty.create("chain_south"), BooleanProperty.create("chain_west"), BooleanProperty.create("chain_east") };
    private final Supplier<Block> stripped;
    public PetrifiedPostBlock(Properties properties, Supplier<Block> stripped) {
        super(properties); this.stripped = stripped;
        var state = defaultBlockState().setValue(WATERLOGGED, false);
        for (var p : CHAINED) state = state.setValue(p, false);
        registerDefaultState(state);
    }
    @Override public MapCodec<? extends RotatedPillarBlock> codec() { return simpleCodec(p -> new PetrifiedPostBlock(p, stripped)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(WATERLOGGED); builder.add(CHAINED);
    }
    @Override public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        return stripped != null && ability == ItemAbilities.AXE_STRIP ? stripped.get().withPropertiesOf(state)
                : super.getToolModifiedState(state, context, ability, simulate);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis())
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER), context.getLevel(), context.getClickedPos());
    }
    private BlockState connections(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            var neighbor = level.getBlockState(pos.relative(d));
            boolean connects = d.getAxis() != state.getValue(AXIS) &&
                    ((neighbor.getBlock() instanceof ChainBlock && neighbor.getValue(ChainBlock.AXIS) == d.getAxis())
                    || (d == Direction.DOWN && neighbor.getBlock() instanceof LanternBlock && neighbor.getValue(LanternBlock.HANGING)));
            state = state.setValue(CHAINED[d.ordinal()], connects);
        }
        return state;
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return connections(state, level, pos);
    }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return transformConnections(state, super.rotate(state, rotation), rotation::rotate);
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return transformConnections(state, state, mirror::mirror);
    }
    private BlockState transformConnections(BlockState original, BlockState transformed, java.util.function.UnaryOperator<Direction> transform) {
        for (Direction direction : Direction.values())
            transformed = transformed.setValue(CHAINED[transform.apply(direction).ordinal()], original.getValue(CHAINED[direction.ordinal()]));
        return transformed;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) { case X -> Block.box(0, 6, 6, 16, 10, 10); case Z -> Block.box(6, 6, 0, 10, 10, 16); default -> Block.box(6, 0, 6, 10, 16, 10); };
    }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        // The lantern's short connector reaches the beam without enlarging its collision shape.
        return Shapes.or(getShape(state, level, pos, CollisionContext.empty()), Block.box(7, 0, 7, 9, 16, 9));
    }
    @Override protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return !state.getValue(WATERLOGGED); }
}
