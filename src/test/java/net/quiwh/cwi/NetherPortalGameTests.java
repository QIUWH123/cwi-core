package net.quiwh.cwi;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class NetherPortalGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void fireCannotActivateValidNetherFrames(GameTestHelper helper) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos interior = frame(helper, axis);
            helper.assertTrue(PortalShape.findEmptyPortalShape(helper.getLevel(), interior, axis).isPresent(),
                    "Fixture is a valid unlit obsidian frame for " + axis);
            helper.getLevel().setBlockAndUpdate(interior, Blocks.FIRE.defaultBlockState());
            for (int across = 0; across < 2; across++) {
                for (int up = 0; up < 3; up++) {
                    BlockPos pos = interior.relative(axis, across).above(up);
                    helper.assertTrue(!helper.getLevel().getBlockState(pos).is(Blocks.NETHER_PORTAL),
                            "Fire must not activate a Nether portal for " + axis);
                }
            }
            helper.assertTrue(helper.getLevel().getBlockState(interior.below()).is(Blocks.OBSIDIAN),
                    "Blocked ignition preserves the obsidian frame");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void destinationCreationDoesNotGenerateOrReplaceBlocks(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        var positions = new ArrayList<BlockPos>();
        var before = new ArrayList<BlockState>();
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-1, -1, -1), target.offset(2, 4, 1))) {
            positions.add(pos.immutable());
            before.add(helper.getLevel().getBlockState(pos));
        }
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            helper.assertTrue(helper.getLevel().getPortalForcer().createPortal(target, axis).isEmpty(),
                    "Automatic Nether destination creation must fail for " + axis);
            for (int i = 0; i < positions.size(); i++) {
                helper.assertTrue(helper.getLevel().getBlockState(positions.get(i)).equals(before.get(i)),
                        "Denied destination creation leaves existing blocks unchanged");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void existingPortalAndOrdinaryFireRemainIntact(GameTestHelper helper) {
        BlockPos interior = frame(helper, Direction.Axis.X);
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        for (int across = 0; across < 2; across++) {
            for (int up = 0; up < 3; up++) {
                helper.getLevel().setBlock(interior.east(across).above(up), portal, 18);
            }
        }
        helper.assertTrue(new PortalShape(helper.getLevel(), interior, Direction.Axis.X).isComplete(),
                "Existing Nether portal blocks remain valid");
        BlockPos fire = helper.absolutePos(new BlockPos(1, 1, 6));
        helper.getLevel().setBlockAndUpdate(fire.below(), Blocks.NETHERRACK.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(fire).is(Blocks.FIRE),
                    "Ordinary fire still burns");
            helper.assertTrue(new PortalShape(helper.getLevel(), interior, Direction.Axis.X).isComplete(),
                    "No cleanup removes existing Nether portals");
            helper.succeed();
        });
    }

    private static BlockPos frame(GameTestHelper helper, Direction.Axis axis) {
        BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int across = 0; across < 4; across++) {
            for (int up = 0; up < 5; up++) {
                BlockPos pos = corner.relative(axis, across).above(up);
                boolean edge = across == 0 || across == 3 || up == 0 || up == 4;
                helper.getLevel().setBlock(pos, (edge ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState(), 18);
            }
        }
        return corner.relative(axis, 1).above();
    }
}
