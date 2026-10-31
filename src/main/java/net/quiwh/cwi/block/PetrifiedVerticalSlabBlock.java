package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;

public final class PetrifiedVerticalSlabBlock extends Block implements SimpleWaterloggedBlock {
    public enum Part implements StringRepresentable {
        NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), WEST(Direction.WEST), EAST(Direction.EAST), DOUBLE(null);
        public final Direction face;
        Part(Direction face) { this.face = face; }
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Part of(Direction face) { return valueOf(face.name()); }
    }
    public static final EnumProperty<Part> TYPE = EnumProperty.create("type", Part.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public PetrifiedVerticalSlabBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(TYPE, Part.NORTH).setValue(WATERLOGGED, false)); }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(PetrifiedVerticalSlabBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(TYPE, WATERLOGGED); }
    private Direction placementFace(BlockPlaceContext context) {
        if (context.getClickedFace().getAxis() != Direction.Axis.Y) return context.getClickedFace();
        var offset = context.getClickLocation().subtract(context.getClickedPos().getX() + .5, 0, context.getClickedPos().getZ() + .5);
        return Direction.fromYRot(-Math.toDegrees(Math.atan2(offset.x, offset.z))).getOpposite();
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var current = context.getLevel().getBlockState(context.getClickedPos());
        if (current.is(this)) return current.setValue(TYPE, Part.DOUBLE).setValue(WATERLOGGED, false);
        return defaultBlockState().setValue(TYPE, Part.of(placementFace(context)))
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }
    @Override protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        var part = state.getValue(TYPE);
        return part != Part.DOUBLE && context.getItemInHand().is(asItem()) &&
                (context.replacingClickedOnBlock() ? context.getClickedFace() == part.face && placementFace(context) == part.face
                        : context.getClickedFace().getAxis() != part.face.getAxis());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(TYPE)) {
            case NORTH -> Block.box(0, 0, 8, 16, 16, 16); case SOUTH -> Block.box(0, 0, 0, 16, 16, 8);
            case WEST -> Block.box(8, 0, 0, 16, 16, 16); case EAST -> Block.box(0, 0, 0, 8, 16, 16); default -> Shapes.block();
        };
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.getValue(TYPE) == Part.DOUBLE ? state : state.setValue(TYPE, Part.of(rotation.rotate(state.getValue(TYPE).face))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.getValue(TYPE) == Part.DOUBLE ? state : state.setValue(TYPE, Part.of(mirror.mirror(state.getValue(TYPE).face))); }
    @Override protected boolean useShapeForLightOcclusion(BlockState state) { return state.getValue(TYPE) != Part.DOUBLE; }
    @Override protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return type == net.minecraft.world.level.pathfinder.PathComputationType.WATER && state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }
    @Override public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) { return state.getValue(TYPE) != Part.DOUBLE && SimpleWaterloggedBlock.super.canPlaceLiquid(player, level, pos, state, fluid); }
    @Override public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) { return state.getValue(TYPE) != Part.DOUBLE && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid); }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbor, level, pos, other);
    }
}
