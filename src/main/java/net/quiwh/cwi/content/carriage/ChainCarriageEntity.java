package net.quiwh.cwi.content.carriage;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-owned chain motion with native Create actors, mounted storage and passengers. */
public final class ChainCarriageEntity extends OrientedContraptionEntity {
    private boolean disassembleRequested;
    private int interpolationSteps;
    private double interpolationX;
    private double interpolationY;
    private double interpolationZ;
    private float interpolationYaw;
    private float interpolationPitch;

    public ChainCarriageEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ChainCarriageEntity create(Level level, ChainCarriageContraption contraption) {
        var entity = new ChainCarriageEntity(CarriageContent.ENTITY.get(), level);
        entity.setContraption(contraption);
        entity.setInitialOrientation(contraption.getInitialFacing());
        entity.startAtInitialYaw();
        entity.setYRot(entity.yaw);
        return entity;
    }

    @Nullable
    public ChainConveyorAttachment attachment() {
        return contraption instanceof ChainCarriageContraption carriage ? carriage.getAttachment() : null;
    }

    public void requestDisassembly() {
        if (!level().isClientSide) disassembleRequested = true;
    }

    @Override
    protected void tickContraption() {
        prevYaw = yaw;
        prevPitch = pitch;
        if (level().isClientSide) {
            interpolateMovement();
            tickActors();
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        ChainConveyorAttachment attachment = attachment();
        if (attachment == null) disassembleRequested = true;
        if (disassembleRequested) {
            setContraptionMotion(Vec3.ZERO);
            if (canRestoreSafely(server)) disassemble();
            return;
        }

        // Keep the attachment and physical entity transactional at unloaded chunk boundaries.
        ChainConveyorAttachment previous = attachment.copy();
        var status = attachment.advance(server);
        if (status == ChainConveyorAttachment.Status.INVALID) {
            disassembleRequested = true;
            setContraptionMotion(Vec3.ZERO);
            if (canRestoreSafely(server)) disassemble();
            return;
        }
        Vec3 hook = attachment.position(server);
        if (hook != null && !isStalled()) {
            Vec3 next = hook.subtract(0, 1.5, 0);
            Vec3 movement = next.subtract(position());
            if (footprintLoaded(server, getBoundingBox().minmax(getBoundingBox().move(movement)))) {
                setPos(next);
                Vec3 direction = attachment.direction(server);
                if (direction != null && direction.horizontalDistanceSqr() > 1e-8) {
                    yaw = yawFromVector(direction);
                    pitch = 0;
                    // Vanilla entity tracking reads these fields; Oriented's angles are separate.
                    setYRot(yaw);
                    setXRot(pitch);
                }
                setContraptionMotion(movement);
            } else {
                ((ChainCarriageContraption) contraption).setAttachment(previous);
                setContraptionMotion(Vec3.ZERO);
            }
        } else {
            ((ChainCarriageContraption) contraption).setAttachment(previous);
            setContraptionMotion(Vec3.ZERO);
        }
        if (footprintLoaded(server, getBoundingBox())) tickActors();
    }

    private static boolean footprintLoaded(ServerLevel level, AABB box) {
        int minX = Mth.floor(box.minX) >> 4, maxX = Mth.floor(box.maxX - 1e-7) >> 4;
        int minZ = Mth.floor(box.minZ) >> 4, maxZ = Mth.floor(box.maxZ - 1e-7) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private boolean canRestoreSafely(ServerLevel level) {
        var transform = makeStructureTransform();
        // Native placement updates all six neighbors, including targets rounded beyond
        // the continuous render bounds. Never let those lookups load another chunk.
        for (var block : contraption.getBlocks().values()) {
            var target = transform.apply(block.pos());
            if (!level.isInWorldBounds(target) || !level.hasChunkAt(target)) return false;
            for (Direction face : Direction.values()) {
                if (!level.hasChunkAt(target.relative(face))) return false;
            }
        }
        for (var block : contraption.getBlocks().values()) {
            var target = transform.apply(block.pos());
            var state = transform.apply(block.state());
            var existing = level.getBlockState(target);
            // Create drops an unplaceable block without unmounting its inventory.
            // Wait with the complete load intact until every target can be restored.
            if (existing.getDestroySpeed(level, target) == -1
                    || state.getCollisionShape(level, target).isEmpty()
                    && !existing.getCollisionShape(level, target).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolationX = x;
        interpolationY = y;
        interpolationZ = z;
        interpolationYaw = yaw;
        interpolationPitch = pitch;
        interpolationSteps = Math.max(1, steps);
    }

    private void interpolateMovement() {
        if (interpolationSteps <= 0) {
            setContraptionMotion(Vec3.ZERO);
            return;
        }
        prevYaw = yaw;
        prevPitch = pitch;
        Vec3 before = position();
        double fraction = 1.0 / interpolationSteps;
        setPos(Mth.lerp(fraction, getX(), interpolationX), Mth.lerp(fraction, getY(), interpolationY),
                Mth.lerp(fraction, getZ(), interpolationZ));
        yaw += Mth.wrapDegrees(interpolationYaw - yaw) * (float) fraction;
        pitch += Mth.wrapDegrees(interpolationPitch - pitch) * (float) fraction;
        setYRot(yaw);
        setXRot(pitch);
        interpolationSteps--;
        setContraptionMotion(position().subtract(before));
    }

    @Override
    public double lerpTargetX() { return interpolationSteps > 0 ? interpolationX : getX(); }

    @Override
    public double lerpTargetY() { return interpolationSteps > 0 ? interpolationY : getY(); }

    @Override
    public double lerpTargetZ() { return interpolationSteps > 0 ? interpolationZ : getZ(); }

    @Override
    public float lerpTargetYRot() { return interpolationSteps > 0 ? interpolationYaw : getYRot(); }

    @Override
    public float lerpTargetXRot() { return interpolationSteps > 0 ? interpolationPitch : getXRot(); }

    @Override
    protected void writeAdditional(CompoundTag tag, HolderLookup.Provider registries, boolean spawnPacket) {
        super.writeAdditional(tag, registries, spawnPacket);
        if (disassembleRequested) tag.putBoolean("CwiDisassemble", true);
    }

    @Override
    protected void readAdditional(CompoundTag tag, boolean spawnPacket) {
        super.readAdditional(tag, spawnPacket);
        disassembleRequested = tag.getBoolean("CwiDisassemble");
        prevYaw = targetYaw = yaw;
        prevPitch = pitch;
        setYRot(yaw);
        setXRot(pitch);
        interpolationSteps = 0;
    }
}
