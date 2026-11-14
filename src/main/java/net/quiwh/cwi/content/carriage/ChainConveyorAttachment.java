package net.quiwh.cwi.content.carriage;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import java.util.Comparator;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Attachment state and movement along Create's native conveyor graph. */
public final class ChainConveyorAttachment {
    public enum Status { MOVED, WAITING, INVALID }
    public static final double MAX_ATTACH_DISTANCE = 1.5;
    private static final double RADIUS = 1.25;
    private static final double HEIGHT = .375;
    private static final double EPSILON = 1e-6;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
    private BlockPos owner;
    private @Nullable BlockPos connection;
    private float progress;
    private boolean reversed;
    private BlockPos heading;

    public ChainConveyorAttachment(BlockPos owner, @Nullable BlockPos connection, float progress,
                                   boolean reversed, BlockPos heading) {
        this.owner = owner.immutable();
        this.connection = connection == null ? null : connection.immutable();
        this.progress = progress;
        this.reversed = reversed;
        this.heading = heading.equals(BlockPos.ZERO)
                ? (connection == null ? new BlockPos(0, 0, 1) : connection.immutable()) : heading.immutable();
    }

    /** Projects onto the two directed native lanes and the continuous wheel arc. */
    public static @Nullable ChainConveyorAttachment find(Level level, Vec3 hook, Direction preferred) {
        if (level.isClientSide || !finite(hook)) return null;
        Candidate best = null;
        Vec3 preference = Vec3.atLowerCornerOf(preferred.getNormal());
        for (var conveyor : LoadedConveyors.in(level)) {
            if (!connected(level, conveyor)) continue;
            conveyor.prepareStats();
            boolean reverse = conveyor.getSpeed() == 0 ? conveyor.reversed : conveyor.getSpeed() < 0;
            Vec3 center = Vec3.atBottomCenterOf(conveyor.getBlockPos()).add(0, HEIGHT, 0);
            if (boxDistanceSquared(hook, center.add(-RADIUS, 0, -RADIUS), center.add(RADIUS, 0, RADIUS))
                    <= MAX_ATTACH_DISTANCE * MAX_ATTACH_DISTANCE) {
                Vec3 radial = hook.subtract(center);
                float angle = wrap((float)Math.toDegrees(Math.atan2(radial.x, radial.z)));
                var attachment = new ChainConveyorAttachment(conveyor.getBlockPos(), null, angle, reverse, new BlockPos(preferred.getNormal()));
                best = nearer(best, new Candidate(attachment, hook.distanceToSqr(loopPoint(conveyor.getBlockPos(), angle)),
                        tangent(angle, reverse).dot(preference)));
            }
            for (BlockPos edge : conveyor.connections) {
                if (!validEdge(conveyor.getBlockPos(), edge) || reciprocal(level, conveyor, edge) == Status.INVALID) continue;
                ConnectionStats stats = stats(conveyor, edge, reverse);
                if (stats == null || boxDistanceSquared(hook, stats.start(), stats.end())
                        > MAX_ATTACH_DISTANCE * MAX_ATTACH_DISTANCE) continue;
                Vec3 delta = stats.end().subtract(stats.start());
                double distance = Math.clamp(hook.subtract(stats.start()).dot(delta.normalize()), 0, stats.chainLength());
                Vec3 point = stats.start().add(delta.normalize().scale(distance));
                var attachment = new ChainConveyorAttachment(conveyor.getBlockPos(), edge, (float)distance, reverse, edge);
                best = nearer(best, new Candidate(attachment, hook.distanceToSqr(point), delta.normalize().dot(preference)));
            }
        }
        return best == null || best.distanceSquared > MAX_ATTACH_DISTANCE * MAX_ATTACH_DISTANCE ? null : best.attachment;
    }

    /** Changes are atomic: an unavailable chunk leaves every saved field unchanged. */
    public Status advance(Level level) {
        if (!validState()) return Status.INVALID;
        var next = copy();
        Status status = next.advanceInternal(level);
        if (status == Status.MOVED) {
            owner = next.owner; connection = next.connection; progress = next.progress;
            reversed = next.reversed; heading = next.heading;
        }
        return status;
    }

    private Status advanceInternal(Level level) {
        double remaining = 1;
        boolean moved = false;
        // Ordinary Create's configured RPM crosses at most a few boundaries. The hard
        // iteration limit also bounds malformed speeds and degenerate saved graphs.
        for (int transitions = 0; transitions < 32; transitions++) {
            if (!level.isInWorldBounds(owner)) return Status.INVALID;
            if (!level.hasChunkAt(owner)) return Status.WAITING;
            var conveyor = loaded(level, owner);
            if (conveyor == null) return Status.INVALID;
            if (connection == null && !connected(level, conveyor)) return Status.INVALID;
            if (connection != null) {
                Status edgeStatus = reciprocal(level, conveyor, connection);
                if (edgeStatus != Status.MOVED) return edgeStatus;
                var existing = stats(conveyor, connection, reversed);
                if (existing == null || progress > existing.chainLength() + .001) return Status.INVALID;
            }
            float rpm = conveyor.getSpeed();
            if (!Float.isFinite(rpm)) return Status.INVALID;
            if (rpm == 0) return moved ? Status.MOVED : Status.WAITING;
            if (connection != null) {
                ConnectionStats stats = stats(conveyor, connection, reversed);
                if (stats == null || progress > stats.chainLength() + .001) return Status.INVALID;
                if (reversed != (rpm < 0)) {
                    owner = owner.offset(connection);
                    connection = connection.multiply(-1);
                    progress = Math.max(0, stats.chainLength() - progress);
                    heading = connection;
                    reversed = rpm < 0;
                    conveyor = loaded(level, owner);
                    if (conveyor == null) return Status.WAITING;
                    stats = stats(conveyor, connection, reversed);
                    if (stats == null) return Status.INVALID;
                }
                double rate = Math.abs(rpm) / 360.0;
                double distance = Math.max(0, stats.chainLength() - progress);
                double time = Math.min(remaining, distance / rate);
                float newProgress = (float)Math.min(stats.chainLength(), progress + time * rate);
                Vec3 from = spanPoint(stats, progress), to = spanPoint(stats, newProgress);
                if (!lineLoaded(level, from, to)) return Status.WAITING;
                progress = newProgress;
                remaining -= time;
                moved |= time > EPSILON;
                if (distance / rate > time + EPSILON) return Status.MOVED;
                BlockPos arrivedFrom = connection;
                owner = owner.offset(connection);
                connection = null;
                progress = wrap(stats.tangentAngle() + 180 + 70 * (reversed ? -1 : 1));
                heading = arrivedFrom;
            } else {
                if (reversed != (rpm < 0)) heading = heading.multiply(-1);
                reversed = rpm < 0;
                BlockPos exit = chooseExit(level, conveyor);
                double rate = Math.abs(rpm) / (1.5 * Math.PI);
                double angle = exit == null ? Double.POSITIVE_INFINITY
                        : distanceInDirection(progress, angle(exit, reversed), reversed);
                double time = Math.min(remaining, angle / rate);
                double delta = time * rate * (reversed ? -1 : 1);
                if (!arcLoaded(level, owner, progress, delta)) return Status.WAITING;
                progress = wrap((float)(progress + delta));
                remaining -= time;
                moved |= time > EPSILON;
                if (exit == null || angle / rate > time + EPSILON) return Status.MOVED;
                Status edgeStatus = reciprocal(level, conveyor, exit);
                if (edgeStatus != Status.MOVED) return edgeStatus;
                connection = exit;
                progress = 0;
                heading = exit;
            }
            if (remaining <= EPSILON) return Status.MOVED;
        }
        return Status.INVALID;
    }

    public @Nullable Vec3 position(Level level) {
        if (!validState()) return null;
        var conveyor = loaded(level, owner);
        if (conveyor == null) return null;
        if (connection == null) return connected(level, conveyor) ? loopPoint(owner, progress) : null;
        if (!conveyor.connections.contains(connection)) return null;
        var stats = stats(conveyor, connection, reversed);
        return stats == null || progress > stats.chainLength() + .001 ? null : spanPoint(stats, progress);
    }

    public @Nullable Vec3 direction(Level level) {
        var conveyor = loaded(level, owner);
        if (!validState() || conveyor == null) return null;
        if (connection == null) return connected(level, conveyor) ? tangent(progress, reversed) : null;
        if (!conveyor.connections.contains(connection)) return null;
        var stats = stats(conveyor, connection, reversed);
        return stats == null ? null : stats.end().subtract(stats.start()).normalize();
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        putPosition(tag, "Owner", owner);
        if (connection != null) putPosition(tag, "Connection", connection);
        tag.putFloat("Progress", progress);
        tag.putBoolean("Reversed", reversed);
        putPosition(tag, "Heading", heading);
        return tag;
    }

    public static @Nullable ChainConveyorAttachment read(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.contains("Progress", Tag.TAG_FLOAT) || !tag.contains("Reversed", Tag.TAG_BYTE)
                || (tag.getByte("Reversed") != 0 && tag.getByte("Reversed") != 1)) return null;
        BlockPos owner = readPosition(tag, "Owner"), heading = readPosition(tag, "Heading");
        BlockPos edge = tag.contains("Connection") ? readPosition(tag, "Connection") : null;
        if (owner == null || heading == null || heading.equals(BlockPos.ZERO)
                || tag.contains("Connection") && edge == null) return null;
        var attachment = new ChainConveyorAttachment(owner, edge, tag.getFloat("Progress"), tag.getBoolean("Reversed"), heading);
        if (!attachment.validState()) return null;
        if (edge != null && attachment.progress > geometry(owner, edge, attachment.reversed).chainLength() + .001) return null;
        return attachment;
    }

    public ChainConveyorAttachment copy() { return new ChainConveyorAttachment(owner, connection, progress, reversed, heading); }
    public BlockPos owner() { return owner; }
    public @Nullable BlockPos connection() { return connection; }
    public float progress() { return progress; }
    public boolean reversed() { return reversed; }

    private boolean validState() {
        return worldPosition(owner) && bounded(heading, 60_000_000) && !heading.equals(BlockPos.ZERO)
                && Float.isFinite(progress) && progress >= 0
                && (connection == null ? progress < 360 : validEdge(owner, connection));
    }

    private @Nullable BlockPos chooseExit(Level level, ChainConveyorBlockEntity conveyor) {
        Vec3 intended = Vec3.atLowerCornerOf(heading).normalize();
        BlockPos best = null;
        double bestDot = -Double.MAX_VALUE;
        for (BlockPos edge : conveyor.connections) {
            if (!validEdge(owner, edge) || reciprocal(level, conveyor, edge) == Status.INVALID) continue;
            double dot = intended.dot(Vec3.atLowerCornerOf(edge).normalize());
            if (dot > bestDot + EPSILON || Math.abs(dot - bestDot) <= EPSILON
                    && (best == null || POSITION_ORDER.compare(edge, best) < 0)) {
                best = edge; bestDot = dot;
            }
        }
        return best;
    }

    private static @Nullable ChainConveyorBlockEntity loaded(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return null;
        return level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity conveyor && !conveyor.isRemoved() ? conveyor : null;
    }

    private static Status reciprocal(Level level, ChainConveyorBlockEntity conveyor, BlockPos edge) {
        if (!validEdge(conveyor.getBlockPos(), edge) || !conveyor.connections.contains(edge)) return Status.INVALID;
        BlockPos target = conveyor.getBlockPos().offset(edge);
        if (!level.isInWorldBounds(target)) return Status.INVALID;
        if (!level.hasChunkAt(target)) return Status.WAITING;
        var other = loaded(level, target);
        return other != null && other.connections.contains(edge.multiply(-1)) ? Status.MOVED : Status.INVALID;
    }

    private static boolean connected(Level level, ChainConveyorBlockEntity conveyor) {
        for (BlockPos edge : conveyor.connections) {
            if (reciprocal(level, conveyor, edge) != Status.INVALID) return true;
        }
        return false;
    }

    private static @Nullable ConnectionStats stats(ChainConveyorBlockEntity conveyor, BlockPos edge, boolean reverse) {
        conveyor.prepareStats();
        var nativeStats = conveyor.connectionStats.get(edge);
        float angle = angle(edge, reverse);
        // prepareStats may already have switched sides before our entity tick. Stored
        // attachment direction owns that old lane until reversal swaps its endpoints.
        if (nativeStats != null && Math.abs(Math.IEEEremainder(nativeStats.tangentAngle() - angle, 360)) < .001
                && Float.isFinite(nativeStats.chainLength()) && nativeStats.chainLength() > EPSILON) return nativeStats;
        var fallback = geometry(conveyor.getBlockPos(), edge, reverse);
        return Float.isFinite(fallback.chainLength()) && fallback.chainLength() > EPSILON ? fallback : null;
    }

    private static ConnectionStats geometry(BlockPos owner, BlockPos edge, boolean reverse) {
        float angle = angle(edge, reverse);
        float opposite = wrap(angle + 180 + 70 * (reverse ? -1 : 1));
        Vec3 start = loopPoint(owner, angle), end = loopPoint(owner.offset(edge), opposite);
        return new ConnectionStats(angle, (float)start.distanceTo(end), start, end);
    }

    private static float angle(BlockPos edge, boolean reverse) {
        return wrap((float)Math.toDegrees(Math.atan2(edge.getX(), edge.getZ())) - 35 * (reverse ? -1 : 1));
    }

    private static Vec3 loopPoint(BlockPos owner, float angle) {
        return Vec3.atBottomCenterOf(owner).add(VecHelper.rotate(new Vec3(0, HEIGHT, RADIUS), angle, Axis.Y));
    }

    private static Vec3 tangent(float angle, boolean reverse) {
        Vec3 first = VecHelper.rotate(new Vec3(0, 0, RADIUS), angle - .01, Axis.Y);
        Vec3 second = VecHelper.rotate(new Vec3(0, 0, RADIUS), angle + .01, Axis.Y);
        return second.subtract(first).normalize().scale(reverse ? -1 : 1);
    }

    private static Vec3 spanPoint(ConnectionStats stats, float position) {
        return stats.start().add(stats.end().subtract(stats.start()).normalize().scale(Math.clamp(position, 0, stats.chainLength())));
    }

    private static boolean lineLoaded(Level level, Vec3 from, Vec3 to) {
        int steps = (int)Math.ceil(from.distanceTo(to) * 4);
        if (steps > 256) return false;
        for (int i = 0; i <= steps; i++) {
            if (!level.hasChunkAt(BlockPos.containing(from.lerp(to, steps == 0 ? 0 : (double)i / steps)))) return false;
        }
        return true;
    }

    private static boolean arcLoaded(Level level, BlockPos owner, float from, double delta) {
        int steps = (int)Math.ceil(Math.abs(delta) / 5);
        if (steps > 256) return false;
        for (int i = 0; i <= steps; i++) {
            Vec3 point = loopPoint(owner, (float)(from + delta * (steps == 0 ? 0 : (double)i / steps)));
            if (!level.hasChunkAt(BlockPos.containing(point))) return false;
        }
        return true;
    }

    private static float wrap(float angle) { return (angle % 360 + 360) % 360; }
    private static double distanceInDirection(float from, float to, boolean reverse) { return wrap(reverse ? from - to : to - from); }
    private static boolean finite(Vec3 point) { return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z); }
    private static boolean worldPosition(BlockPos pos) { return Math.abs((long)pos.getX()) <= 30_000_000
            && Math.abs((long)pos.getZ()) <= 30_000_000 && Math.abs((long)pos.getY()) <= 20_000_000; }
    private static boolean bounded(BlockPos pos, long limit) { return Math.abs((long)pos.getX()) <= limit
            && Math.abs((long)pos.getY()) <= limit && Math.abs((long)pos.getZ()) <= limit; }
    private static boolean validEdge(BlockPos owner, BlockPos edge) {
        if (edge.equals(BlockPos.ZERO) || !bounded(edge, 60_000_000) || !worldPosition(owner.offset(edge))) return false;
        Vec3 vector = Vec3.atLowerCornerOf(edge);
        double horizontal = Math.hypot(vector.x, vector.z) - 1.5;
        return vector.lengthSqr() >= 2.5 * 2.5 && horizontal > 0 && Math.abs(vector.y) <= horizontal + EPSILON;
    }

    private static void putPosition(CompoundTag tag, String name, BlockPos pos) {
        tag.putIntArray(name, new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    private static @Nullable BlockPos readPosition(CompoundTag tag, String name) {
        if (!tag.contains(name, Tag.TAG_INT_ARRAY)) return null;
        int[] values = tag.getIntArray(name);
        return values.length == 3 ? new BlockPos(values[0], values[1], values[2]) : null;
    }

    private static double boxDistanceSquared(Vec3 point, Vec3 a, Vec3 b) {
        return new AABB(a, b).distanceToSqr(point);
    }

    private static Candidate nearer(@Nullable Candidate best, Candidate candidate) {
        if (best == null || candidate.distanceSquared < best.distanceSquared - EPSILON) return candidate;
        if (Math.abs(candidate.distanceSquared - best.distanceSquared) > EPSILON) return best;
        if (candidate.alignment > best.alignment + EPSILON) return candidate;
        if (Math.abs(candidate.alignment - best.alignment) > EPSILON) return best;
        int ownerOrder = POSITION_ORDER.compare(candidate.attachment.owner, best.attachment.owner);
        if (ownerOrder != 0) return ownerOrder < 0 ? candidate : best;
        BlockPos edgeA = candidate.attachment.connection, edgeB = best.attachment.connection;
        if (edgeA == null || edgeB == null) return edgeA == null ? candidate : best;
        return POSITION_ORDER.compare(edgeA, edgeB) < 0 ? candidate : best;
    }

    private record Candidate(ChainConveyorAttachment attachment, double distanceSquared, double alignment) { }
}
