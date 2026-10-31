package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class TrimmedPetrifiedPlanksBlock extends Block {
    public enum Part implements StringRepresentable {
        DEFAULT, LOWER, MIDDLE, UPPER;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Part> PART = EnumProperty.create("pillar_state", Part.class);
    public TrimmedPetrifiedPlanksBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(PART, Part.DEFAULT)); }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(TrimmedPetrifiedPlanksBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return connected(context.getLevel(), context.getClickedPos()); }
    private BlockState connected(LevelAccessor level, BlockPos pos) {
        boolean above = level.getBlockState(pos.above()).is(this), below = level.getBlockState(pos.below()).is(this);
        return defaultBlockState().setValue(PART, above ? below ? Part.MIDDLE : Part.LOWER : below ? Part.UPPER : Part.DEFAULT);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        return direction.getAxis() == Direction.Axis.Y ? connected(level, pos) : state;
    }
}
