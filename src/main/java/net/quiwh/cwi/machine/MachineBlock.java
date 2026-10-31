package net.quiwh.cwi.machine;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MachineBlock extends KineticBlock implements EntityBlock {
    public enum Kind { BLAST_FURNACE, INCUBATOR, MIXING_VESSEL, STIRLING_ENGINE, LAVA_EXTRACTOR_HEAD,
        LAVA_EXTRACTOR_PUMP, LAVA_EXTRACTOR_STRESS_INPUT, LAVA_EXTRACTOR_PUMP_BLADE, SOLAR_EVAPORATOR }
    public enum Shaft { NONE, DOWN, VERTICAL, FRONT }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    private final Kind kind;
    private final Shaft shaft;

    public MachineBlock(Properties properties, Kind kind, Shaft shaft) {
        super(properties);
        this.kind = kind;
        this.shaft = shaft;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
    }

    @Override protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new MachineBlock(properties, kind, shaft));
    }
    public Kind kind() { return kind; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.MIXING_VESSEL) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return MachineInteractions.use(stack, level, pos, player, hand, hit);
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return kind == Kind.MIXING_VESSEL; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return kind == Kind.MIXING_VESSEL && level.getBlockEntity(pos) instanceof MixingVesselBlockEntity vessel ? vessel.comparatorSignal() : 0;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public Direction.Axis getRotationAxis(BlockState state) {
        return shaft == Shaft.FRONT ? state.getValue(FACING).getAxis() : Direction.Axis.Y;
    }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return switch (shaft) {
            case NONE -> false;
            case DOWN -> face == Direction.DOWN;
            case VERTICAL -> face.getAxis() == Direction.Axis.Y;
            case FRONT -> face == state.getValue(FACING);
        };
    }
    @Override protected boolean areStatesKineticallyEquivalent(BlockState before, BlockState after) {
        return super.areStatesKineticallyEquivalent(before, after)
                && (shaft != Shaft.FRONT || before.getValue(FACING) == after.getValue(FACING));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MachineShapes.shape(kind.name().toLowerCase(java.util.Locale.ROOT), state.getValue(FACING));
    }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case BLAST_FURNACE -> new BlastFurnaceBlockEntity(pos, state);
            case INCUBATOR -> new IncubatorBlockEntity(pos, state);
            case MIXING_VESSEL -> new MixingVesselBlockEntity(pos, state);
            case STIRLING_ENGINE -> new StirlingEngineBlockEntity(pos, state);
            case LAVA_EXTRACTOR_HEAD -> new LavaExtractorBlockEntity(pos, state);
            case LAVA_EXTRACTOR_PUMP, LAVA_EXTRACTOR_STRESS_INPUT, LAVA_EXTRACTOR_PUMP_BLADE -> new ExtractorKineticBlockEntity(pos, state);
            case SOLAR_EVAPORATOR -> new SolarEvaporatorBlockEntity(pos, state);
        };
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return new SmartBlockEntityTicker<>();
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) machine.dropContents();
        super.onRemove(state, level, pos, replacement, moved);
    }
}
