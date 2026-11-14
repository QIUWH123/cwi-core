package net.quiwh.cwi.content.carriage;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ChainCarriageBlockEntity extends SmartBlockEntity implements IDisplayAssemblyExceptions {
    private boolean assembleNextTick;
    @Nullable private AssemblyException lastException;

    public ChainCarriageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public void queueAssembly() { assembleNextTick = true; }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !assembleNextTick) return;
        assembleNextTick = false;
        assemble();
        if (!isRemoved()) { setChanged(); sendData(); }
    }

    private void assemble() {
        var attachment = ChainConveyorAttachment.find(level, Vec3.atCenterOf(worldPosition).add(0, 1, 0),
                getBlockState().getValue(ChainCarriageBlock.FACING));
        if (attachment == null) {
            lastException = new AssemblyException("cwi.no_conveyor_chain");
            return;
        }
        Vec3 tangent = attachment.direction(level);
        if (tangent == null || tangent.horizontalDistanceSqr() < 1e-8) {
            lastException = new AssemblyException("cwi.no_conveyor_chain");
            return;
        }
        Direction facing = Direction.getNearest(tangent.x, 0, tangent.z);
        BlockState original = getBlockState();
        level.setBlock(worldPosition, original.setValue(ChainCarriageBlock.FACING, facing), 3);
        var contraption = new ChainCarriageContraption(attachment);
        try {
            if (!contraption.assemble(level, worldPosition)) {
                level.setBlock(worldPosition, original, 3);
                return;
            }
            lastException = null;
        } catch (AssemblyException failure) {
            lastException = failure;
            level.setBlock(worldPosition, original, 3);
            return;
        }
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        ChainCarriageEntity entity = ChainCarriageEntity.create(level, contraption);
        entity.setPos(Vec3.atBottomCenterOf(worldPosition));
        if (!level.addFreshEntity(entity)) {
            // Entity-join cancellation must not delete the assembled structure or its storage.
            entity.disassemble();
            return;
        }
        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);
    }

    @Override public @Nullable AssemblyException getLastAssemblyException() { return lastException; }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        AssemblyException.write(tag, registries, lastException);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        lastException = AssemblyException.read(tag, registries);
    }
}
