package net.quiwh.cwi.content.carriage;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ChainCarriageContraption extends Contraption {
    @Nullable private ChainConveyorAttachment attachment;
    private Direction initialFacing = Direction.SOUTH;

    public ChainCarriageContraption() {}
    public ChainCarriageContraption(ChainConveyorAttachment attachment) { this.attachment = attachment; }

    @Override public boolean assemble(Level level, BlockPos pos) throws AssemblyException {
        if (attachment == null || attachment.position(level) == null) return false;
        initialFacing = level.getBlockState(pos).getValue(ChainCarriageBlock.FACING);
        if (!searchMovedStructure(level, pos, null) || blocks.isEmpty()) return false;
        startMoving(level);
        expandBoundsAroundAxis(Direction.Axis.Y);
        return true;
    }

    @Override protected boolean addToInitialFrontier(Level level, BlockPos pos, Direction forcedDirection,
            Queue<BlockPos> frontier) {
        frontier.add(pos.below());
        return true;
    }

    @Override protected boolean isAnchoringBlockAt(BlockPos pos) {
        return attachment != null && (pos.equals(attachment.owner())
                || attachment.connection() != null && pos.equals(attachment.owner().offset(attachment.connection())));
    }

    @Override public boolean canBeStabilized(Direction facing, BlockPos localPos) { return false; }
    @Override public ContraptionType getType() { return CarriageContent.CONTRAPTION.value(); }
    public @Nullable ChainConveyorAttachment getAttachment() { return attachment; }
    public void setAttachment(ChainConveyorAttachment attachment) { this.attachment = attachment; }
    public Direction getInitialFacing() { return initialFacing; }

    @Override public CompoundTag writeNBT(HolderLookup.Provider registries, boolean packet) {
        CompoundTag tag = super.writeNBT(registries, packet);
        if (attachment != null) tag.put("ConveyorAttachment", attachment.write());
        tag.putString("CarriageFacing", initialFacing.getSerializedName());
        return tag;
    }

    @Override public void readNBT(Level level, CompoundTag tag, boolean packet) {
        attachment = ChainConveyorAttachment.read(tag.getCompound("ConveyorAttachment"));
        Direction facing = Direction.byName(tag.getString("CarriageFacing"));
        initialFacing = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        super.readNBT(level, tag, packet);
    }
}
