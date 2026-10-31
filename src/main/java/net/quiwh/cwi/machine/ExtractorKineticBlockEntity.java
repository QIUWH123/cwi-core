package net.quiwh.cwi.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class ExtractorKineticBlockEntity extends MachineBlockEntity {
    public ExtractorKineticBlockEntity(BlockPos pos, BlockState state) { super(type(state), pos, state, 0); }
    private static BlockEntityType<?> type(BlockState state) {
        if (state.is(MachineBlocks.LAVA_EXTRACTOR_PUMP.get())) return MachineBlocks.LAVA_EXTRACTOR_PUMP_ENTITY.get();
        if (state.is(MachineBlocks.LAVA_EXTRACTOR_STRESS_INPUT.get())) return MachineBlocks.LAVA_EXTRACTOR_STRESS_INPUT_ENTITY.get();
        return MachineBlocks.LAVA_EXTRACTOR_PUMP_BLADE_ENTITY.get();
    }
    @Override public float minimumRequiredRpm() { return getBlockState().is(MachineBlocks.LAVA_EXTRACTOR_PUMP.get()) ? 128 : 0; }
    @Override protected AABB createRenderBoundingBox() { return new AABB(worldPosition).inflate(2); }
}
