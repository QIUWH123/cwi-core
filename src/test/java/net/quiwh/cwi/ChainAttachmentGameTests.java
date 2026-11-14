package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.content.carriage.ChainConveyorAttachment;
import net.quiwh.cwi.content.carriage.LoadedConveyors;

/** Native Create geometry, lifecycle and boundary contracts for the carriage attachment. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ChainAttachmentGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void nativeSpanSpeedStopReverseAndReload(GameTestHelper helper) {
        var a = conveyor(helper, new BlockPos(2, 5, 3));
        var b = conveyor(helper, new BlockPos(11, 5, 3));
        connect(a, b); speed(90, a, b);
        BlockPos edge = b.getBlockPos().subtract(a.getBlockPos());
        var attachment = new ChainConveyorAttachment(a.getBlockPos(), edge, 2, false, edge);
        Vec3 before = attachment.position(helper.getLevel());
        helper.assertTrue(before != null, "a reciprocal loaded native span resolves");
        helper.assertTrue(attachment.advance(helper.getLevel()) == ChainConveyorAttachment.Status.MOVED,
                "a powered native span moves");
        close(helper, before.distanceTo(attachment.position(helper.getLevel())), .25,
                "native span advances RPM/360 blocks each tick");
        Vec3 stopped = attachment.position(helper.getLevel());
        speed(0, a, b);
        helper.assertTrue(attachment.advance(helper.getLevel()) == ChainConveyorAttachment.Status.WAITING,
                "zero RPM pauses the attachment");
        close(helper, stopped.distanceTo(attachment.position(helper.getLevel())), 0, "stop preserves position");
        var loaded = ChainConveyorAttachment.read(attachment.write());
        helper.assertTrue(loaded != null, "attachment restores its authoritative path state");
        speed(-90, a, b);
        close(helper, stopped.distanceTo(loaded.position(helper.getLevel())), 0,
                "native stat reversal does not move the attachment before its tick");
        helper.assertTrue(loaded.advance(helper.getLevel()) == ChainConveyorAttachment.Status.MOVED
                        && loaded.owner().equals(b.getBlockPos()) && loaded.connection().equals(edge.multiply(-1)),
                "reversal exchanges directed span ownership");
        close(helper, stopped.distanceTo(loaded.position(helper.getLevel())), .25,
                "reverse travel preserves the physical lane without a cross-chain jump");
        helper.assertTrue(loaded.direction(helper.getLevel()).dot(Vec3.atLowerCornerOf(edge)) < 0,
                "reverse tangent points back along the original span");
        speed(0, a, b);
        var stoppedReload = ChainConveyorAttachment.read(loaded.write());
        a.connectionStats = b.connectionStats = null;
        close(helper, loaded.position(helper.getLevel()).distanceTo(stoppedReload.position(helper.getLevel())), 0,
                "negative-side path survives stopped native stat reconstruction");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void slopesAndContinuousLoopTransitions(GameTestHelper helper) {
        var a = conveyor(helper, new BlockPos(2, 3, 3));
        var b = conveyor(helper, new BlockPos(10, 6, 3));
        connect(a, b); speed(90, a, b);
        BlockPos edge = b.getBlockPos().subtract(a.getBlockPos());
        var stats = a.connectionStats.get(edge);
        var attachment = new ChainConveyorAttachment(a.getBlockPos(), edge, stats.chainLength() - .01F, false, edge);
        Vec3 before = attachment.position(helper.getLevel());
        helper.assertTrue(attachment.direction(helper.getLevel()).y > 0,
                "native sloped span retains its vertical direction");
        helper.assertTrue(attachment.advance(helper.getLevel()) == ChainConveyorAttachment.Status.MOVED
                        && attachment.owner().equals(b.getBlockPos()) && attachment.connection() == null,
                "span arrives on the destination loop");
        helper.assertTrue(before.distanceTo(attachment.position(helper.getLevel())) < .5,
                "rigid hook has no radial jump from a native span to its loop");
        Vec3 atLoop = attachment.position(helper.getLevel());
        close(helper, Math.hypot(atLoop.x - b.getBlockPos().getX() - .5,
                atLoop.z - b.getBlockPos().getZ() - .5), 1.25, "loop matches native span endpoint radius");
        close(helper, atLoop.y, b.getBlockPos().getY() + .375, "loop matches native span endpoint height");
        speed(-90, a, b);
        helper.assertTrue(attachment.advance(helper.getLevel()) == ChainConveyorAttachment.Status.MOVED
                        && attachment.connection() != null,
                "loop reversal selects the former incoming edge and resumes backward travel");
        close(helper, attachment.position(helper.getLevel()).distanceTo(before), 0,
                "one reversed tick exactly retraces span arrival and its loop overshoot");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void junctionChoosesStraightestOutgoingAndCarriesOvershoot(GameTestHelper helper) {
        var center = conveyor(helper, new BlockPos(7, 5, 7));
        var west = conveyor(helper, new BlockPos(2, 5, 7));
        var east = conveyor(helper, new BlockPos(12, 5, 7));
        var north = conveyor(helper, new BlockPos(7, 5, 2));
        connect(center, north); connect(center, west); connect(center, east);
        speed(90, center, west, east, north);
        BlockPos selected = east.getBlockPos().subtract(center.getBlockPos());
        float tangent = center.connectionStats.get(selected).tangentAngle();
        var attachment = new ChainConveyorAttachment(center.getBlockPos(), null,
                center.wrapAngle(tangent - .01F), false, new BlockPos(1, 0, 0));
        Vec3 before = attachment.position(helper.getLevel());
        helper.assertTrue(attachment.advance(helper.getLevel()) == ChainConveyorAttachment.Status.MOVED
                        && selected.equals(attachment.connection()) && attachment.progress() > 0,
                "straightest route is independent of connection insertion order and preserves remaining tick time");
        helper.assertTrue(before.distanceTo(attachment.position(helper.getLevel())) < .5,
                "loop departure stays continuous while crossing the selected tangent");
        var duplicate = attachment.copy();
        attachment.advance(helper.getLevel());
        helper.assertTrue(duplicate.progress() < attachment.progress(), "copy has independent mutable progress");
        BlockPos tieWinner = west.getBlockPos().subtract(center.getBlockPos());
        var tied = new ChainConveyorAttachment(center.getBlockPos(), null,
                center.wrapAngle(center.connectionStats.get(tieWinner).tangentAngle() - .01F),
                false, new BlockPos(0, 0, 1));
        tied.advance(helper.getLevel());
        helper.assertTrue(tieWinner.equals(tied.connection()),
                "equal straightness chooses the lexicographically first relative endpoint");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void indexedDiscoveryProjectsDistantNativeSpansAndPrunesRemovedEntities(GameTestHelper helper) {
        var a = conveyor(helper, new BlockPos(2, 5, 3));
        var b = conveyor(helper, new BlockPos(11, 5, 3));
        connect(a, b); speed(90, a, b);
        var stats = a.connectionStats.get(b.getBlockPos().subtract(a.getBlockPos()));
        Vec3 hook = stats.start().lerp(stats.end(), .5);
        var found = ChainConveyorAttachment.find(helper.getLevel(), hook, Direction.EAST);
        helper.assertTrue(found != null && found.connection() != null,
                "assembly discovers the middle of an indexed native chain without searching nearby blocks");
        close(helper, found.position(helper.getLevel()).distanceTo(hook), 0, "discovery projects onto the closest directed lane");
        helper.assertTrue(ChainConveyorAttachment.find(helper.getLevel(), hook.add(0, 3, 0), Direction.EAST) == null,
                "discovery has a bounded physical attachment distance");
        a.connections.clear(); b.connections.clear();
        Vec3 wheelHook = Vec3.atBottomCenterOf(a.getBlockPos()).add(1.25, .375, 0);
        helper.assertTrue(ChainConveyorAttachment.find(helper.getLevel(), wheelHook, Direction.EAST) == null,
                "a bare wheel without a chain is not an attachment candidate");
        var onLoop = new ChainConveyorAttachment(a.getBlockPos(), null, 90, false, new BlockPos(1, 0, 0));
        helper.assertTrue(onLoop.advance(helper.getLevel()) == ChainConveyorAttachment.Status.INVALID,
                "removing the final connection invalidates a loop attachment");
        helper.getLevel().setBlock(a.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(b.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(ChainConveyorAttachment.find(helper.getLevel(), hook, Direction.EAST) == null,
                "weak loaded-controller index cannot return removed native entities");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void unloadedEndpointsWaitAndBrokenReciprocalInvalidates(GameTestHelper helper) {
        var a = conveyor(helper, new BlockPos(2, 5, 3));
        var b = conveyor(helper, new BlockPos(11, 5, 3));
        connect(a, b); speed(90, a, b);
        BlockPos edge = b.getBlockPos().subtract(a.getBlockPos());
        var attachment = new ChainConveyorAttachment(a.getBlockPos(), edge, 1, false, edge);
        b.connections.remove(edge.multiply(-1));
        speed(0, a, b);
        helper.assertTrue(attachment.advance(helper.getLevel()) == ChainConveyorAttachment.Status.INVALID,
                "a loaded endpoint without its reciprocal edge invalidates even a stopped attachment");
        speed(90, a, b);
        BlockPos missing = a.getBlockPos().offset(1_000_000, 0, 0);
        helper.assertTrue(!helper.getLevel().hasChunkAt(missing), "boundary fixture endpoint is unloaded");
        BlockPos distant = missing.subtract(a.getBlockPos());
        a.connections.add(distant); a.connectionStats = null;
        var waiting = new ChainConveyorAttachment(a.getBlockPos(), distant, 1, false, distant);
        helper.assertTrue(waiting.advance(helper.getLevel()) == ChainConveyorAttachment.Status.WAITING
                        && waiting.progress() == 1 && !helper.getLevel().hasChunkAt(missing),
                "unloaded endpoint pauses without forcing a chunk or advancing saved progress");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void attachmentSerializationRejectsCorruptCoordinatesAndProgress(GameTestHelper helper) {
        var original = new ChainConveyorAttachment(new BlockPos(10, -20, 30), new BlockPos(8, 2, 1),
                3.25F, true, new BlockPos(-8, -2, -1));
        var read = ChainConveyorAttachment.read(original.write());
        helper.assertTrue(read != null && read.owner().equals(original.owner())
                        && read.connection().equals(original.connection()) && read.progress() == original.progress()
                        && read.reversed(), "save roundtrip retains exact directed path state");
        helper.assertTrue(ChainConveyorAttachment.read(new CompoundTag()) == null, "missing path data is rejected");
        var corrupt = original.write(); corrupt.putFloat("Progress", Float.NaN);
        helper.assertTrue(ChainConveyorAttachment.read(corrupt) == null, "nonfinite progress is rejected");
        corrupt = original.write(); corrupt.putFloat("Progress", -1);
        helper.assertTrue(ChainConveyorAttachment.read(corrupt) == null, "negative span progress is rejected");
        corrupt = original.write(); corrupt.putString("Owner", "invalid");
        helper.assertTrue(ChainConveyorAttachment.read(corrupt) == null, "malformed owner coordinates are rejected");
        helper.succeed();
    }

    private static ChainConveyorBlockEntity conveyor(GameTestHelper helper, BlockPos local) {
        helper.setBlock(local, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        ChainConveyorBlockEntity entity = helper.getBlockEntity(local);
        entity.initialize(); entity.prepareStats(); LoadedConveyors.add(entity);
        return entity;
    }

    private static void connect(ChainConveyorBlockEntity a, ChainConveyorBlockEntity b) {
        a.addConnectionTo(b.getBlockPos()); b.addConnectionTo(a.getBlockPos());
        a.prepareStats(); b.prepareStats();
    }

    private static void speed(float rpm, ChainConveyorBlockEntity... conveyors) {
        for (var conveyor : conveyors) { conveyor.setSpeed(rpm); conveyor.prepareStats(); }
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-4, message + ": " + actual + " != " + expected);
    }
}
