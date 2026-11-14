package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class FirePlacementGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void framesDoNotPermitUnsupportedFire(GameTestHelper helper) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos interior = frame(helper, axis);
            for (BlockPos target : new BlockPos[]{interior.above(), interior.above(2)}) {
                helper.assertTrue(!BaseFireBlock.getState(helper.getLevel(), target).canSurvive(helper.getLevel(), target),
                        "Unsupported fixture has no ordinary fire support for " + axis);
                for (Direction facing : Direction.values()) {
                    helper.assertTrue(!BaseFireBlock.canBePlacedAt(helper.getLevel(), target, facing),
                            "Portal frame must not permit unsupported ignition for " + axis + "/" + facing);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void manualUnsupportedIgnitionFailsWithoutCost(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos interior = frame(helper, axis);
            player.setYRot(axis == Direction.Axis.X ? 0 : -90);
            for (boolean ceiling : new boolean[]{false, true}) {
                BlockPos target = interior.above(ceiling ? 2 : 1);
                Direction face = ceiling ? Direction.DOWN : Direction.get(Direction.AxisDirection.POSITIVE, axis);
                BlockPos clicked = target.relative(face.getOpposite());
                for (var item : new net.minecraft.world.item.Item[]{Items.FLINT_AND_STEEL, Items.FIRE_CHARGE}) {
                    ItemStack stack = new ItemStack(item);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false));
                    helper.assertTrue(item.useOn(context) == InteractionResult.FAIL,
                            "Unsupported ignition fails for " + item + "/" + axis + "/ceiling=" + ceiling);
                    helper.assertTrue(stack.getCount() == 1 && stack.getDamageValue() == 0,
                            "Failed ignition must not consume or damage the ignition item");
                    helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                            "Failed ignition leaves no fire");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void dispenserUnsupportedIgnitionDoesNotWearTool(GameTestHelper helper) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos target = frame(helper, axis).above();
            Direction facing = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            BlockPos dispenserPos = target.relative(facing.getOpposite());
            var state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing);
            helper.getLevel().setBlockAndUpdate(dispenserPos, state);
            var entity = (DispenserBlockEntity) helper.getLevel().getBlockEntity(dispenserPos);
            ItemStack stack = new ItemStack(Items.FLINT_AND_STEEL);
            ItemStack result = DispenserBlock.DISPENSER_REGISTRY.get(Items.FLINT_AND_STEEL).dispense(
                    new BlockSource(helper.getLevel(), dispenserPos, state, entity), stack);
            helper.assertTrue(result.getDamageValue() == 0 && result.getCount() == 1,
                    "Dispenser must not wear flint and steel for unsupported frame ignition on " + axis);
            helper.assertTrue(helper.getLevel().getBlockState(target).isAir(),
                    "Unsupported dispenser ignition leaves no fire");
            helper.getLevel().removeBlock(dispenserPos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void ordinaryFirePlacementRulesRemain(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.assertTrue(!BaseFireBlock.canBePlacedAt(helper.getLevel(), target, Direction.NORTH),
                "Open air does not support fire");
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.STONE, Blocks.OBSIDIAN, Blocks.SOUL_SAND}) {
            helper.getLevel().setBlockAndUpdate(target.below(), block.defaultBlockState());
            helper.assertTrue(BaseFireBlock.canBePlacedAt(helper.getLevel(), target, Direction.NORTH),
                    "Ordinary support remains valid on " + block);
        }
        helper.assertTrue(BaseFireBlock.getState(helper.getLevel(), target).is(Blocks.SOUL_FIRE),
                "Soul fire still uses its native base");
        helper.getLevel().removeBlock(target.below(), false);
        for (Direction side : new Direction[]{Direction.EAST, Direction.UP}) {
            helper.getLevel().setBlockAndUpdate(target.relative(side), Blocks.OAK_PLANKS.defaultBlockState());
            helper.assertTrue(BaseFireBlock.canBePlacedAt(helper.getLevel(), target, Direction.NORTH),
                    "Flammable neighbor supports ordinary fire on " + side);
            helper.getLevel().removeBlock(target.relative(side), false);
        }
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos interior = frame(helper, axis);
            helper.assertTrue(BaseFireBlock.canBePlacedAt(helper.getLevel(), interior, Direction.NORTH),
                    "The obsidian frame floor still supports ordinary fire on " + axis);
        }
        helper.succeed();
    }

    private static BlockPos frame(GameTestHelper helper, Direction.Axis axis) {
        BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int across = 0; across < 4; across++) {
            for (int up = 0; up < 5; up++) {
                boolean edge = across == 0 || across == 3 || up == 0 || up == 4;
                helper.getLevel().setBlock(corner.relative(axis, across).above(up),
                        (edge ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState(), 18);
            }
        }
        BlockPos interior = corner.relative(axis, 1).above();
        helper.assertTrue(PortalShape.findEmptyPortalShape(helper.getLevel(), interior, axis).isPresent(),
                "Fixture is a complete unlit frame on " + axis);
        return interior;
    }
}
