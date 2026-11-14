package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.bracket.BracketBlock;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.content.weathered.WeatheredContent;

/** Real Create item, block-entity and kinetic-network contracts without the source addon. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class WeatheredGirderGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void shaftsConsumeAndWrenchRefundWithWeatheredState(GameTestHelper helper) {
        int fixture = 0;
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            for (boolean wet : new boolean[]{false, true}) {
                BlockPos pos = new BlockPos(2 + fixture++ * 2, 3, 3);
                helper.setBlock(pos.above(), Blocks.STONE);
                helper.setBlock(pos.below(), Blocks.STONE);
                BlockState original = WeatheredContent.GIRDER.getDefaultState()
                        .setValue(GirderBlock.X, axis == Direction.Axis.Z)
                        .setValue(GirderBlock.Z, axis == Direction.Axis.X)
                        .setValue(GirderBlock.AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X)
                        .setValue(GirderBlock.TOP, true).setValue(GirderBlock.BOTTOM, true)
                        .setValue(BlockStateProperties.WATERLOGGED, wet);
                helper.setBlock(pos, original);
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                var wrongItem = new ItemStack(Items.STICK, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, wrongItem);
                useBlock(helper, pos, player, wrongItem, Direction.UP);
                helper.assertTrue(helper.getBlockState(pos) == original && wrongItem.getCount() == 2,
                        "an unrelated item cannot capture a shaft or mutate the girder");

                var shafts = AllBlocks.SHAFT.asStack(2);
                player.setItemInHand(InteractionHand.MAIN_HAND, shafts);
                helper.assertTrue(useBlock(helper, pos, player, shafts, Direction.UP).consumesAction(),
                        "native block interaction accepts a Create shaft");
                BlockState captured = helper.getBlockState(pos);
                helper.assertTrue(captured.is(WeatheredContent.GIRDER_SHAFT.get())
                                && captured.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) == axis
                                && captured.getValue(BlockStateProperties.WATERLOGGED) == wet
                                && captured.getValue(GirderBlock.TOP) && captured.getValue(GirderBlock.BOTTOM)
                                && shafts.getCount() == 1,
                        "survival capture consumes exactly one shaft and retains water and flanges: " + axis + "/" + wet);
                KineticBlockEntity entity = helper.getBlockEntity(pos);
                helper.assertTrue(entity.getType() == WeatheredContent.ENCASED_SHAFT.get()
                                && entity.getType().isValid(captured)
                                && captured.getFluidState().getType() == (wet ? Fluids.WATER : Fluids.EMPTY),
                        "captured shaft has its valid registered kinetic entity and fluid state");
                var drops = Block.getDrops(captured, helper.getLevel(), helper.absolutePos(pos), entity, player,
                        new ItemStack(Items.DIAMOND_PICKAXE));
                helper.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum() == 2
                                && drops.stream().filter(stack -> stack.is(AllBlocks.SHAFT.asItem())).mapToInt(ItemStack::getCount).sum() == 1
                                && drops.stream().filter(stack -> stack.is(WeatheredContent.GIRDER.asItem())).mapToInt(ItemStack::getCount).sum() == 1,
                        "breaking the captured block returns one shaft and one weathered girder");

                player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
                int before = player.getInventory().countItem(AllBlocks.SHAFT.asItem());
                helper.assertTrue(player.getMainHandItem().getItem().useOn(context(helper, pos, player, Direction.UP)).consumesAction(),
                        "native Create wrench unwraps the captured shaft");
                BlockState unwrapped = helper.getBlockState(pos);
                helper.assertTrue(unwrapped.is(WeatheredContent.GIRDER.get())
                                && unwrapped.getValue(BlockStateProperties.WATERLOGGED) == wet
                                && unwrapped.getValue(GirderBlock.TOP) && unwrapped.getValue(GirderBlock.BOTTOM)
                                && unwrapped.getValue(GirderBlock.X) == (axis == Direction.Axis.Z)
                                && unwrapped.getValue(GirderBlock.Z) == (axis == Direction.Axis.X)
                                && player.getInventory().countItem(AllBlocks.SHAFT.asItem()) == before + 1
                                && helper.getLevel().getBlockEntity(helper.absolutePos(pos)) == null,
                        "wrench restores weathered girder state, refunds one shaft and removes its kinetic entity");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void encasedShaftReloadsAndTransmitsLiveRotation(GameTestHelper helper) {
        int fixture = 0;
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos pos = new BlockPos(3, 3, 2 + fixture++ * 4);
            Direction forward = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            BlockPos motorPos = pos.relative(forward.getOpposite()), outputPos = pos.relative(forward);
            BlockState state = WeatheredContent.GIRDER_SHAFT.getDefaultState()
                    .setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, axis)
                    .setValue(BlockStateProperties.WATERLOGGED, true);
            helper.setBlock(pos, state);
            helper.setBlock(outputPos, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, axis));
            helper.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, forward));
            CreativeMotorBlockEntity motor = helper.getBlockEntity(motorPos);
            motor.generatedSpeed.setValue(64);
            motor.initialize();
            KineticBlockEntity shaft = helper.getBlockEntity(pos), output = helper.getBlockEntity(outputPos);
            shaft.initialize(); shaft.attachKinetics();
            output.initialize(); output.attachKinetics();
            helper.assertTrue(Math.abs(shaft.getSpeed()) == 64 && Math.abs(output.getSpeed()) == 64
                            && shaft.hasSource() && shaft.source.equals(helper.absolutePos(motorPos)),
                    "a real motor drives the weathered shaft and its downstream Create shaft: " + axis);

            var saved = shaft.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(saved.getString("id").equals("cwi:weathered_girder_encased_shaft"),
                    "saved kinetic entity uses the registered Core identity");
            shaft.detachKinetics(); shaft.setRemoved();
            BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), helper.getBlockState(pos), saved,
                    helper.getLevel().registryAccess());
            helper.assertTrue(loaded instanceof KineticBlockEntity && loaded.getType() == WeatheredContent.ENCASED_SHAFT.get(),
                    "native block-entity deserialization resolves the weathered shaft type");
            var restored = (KineticBlockEntity) loaded;
            restored.setLevel(helper.getLevel());
            helper.getLevel().setBlockEntity(restored);
            restored.initialize(); restored.attachKinetics(); output.attachKinetics();
            helper.assertTrue(restored.hasSource() && restored.source.equals(helper.absolutePos(motorPos))
                            && restored.getBlockState().getValue(BlockStateProperties.WATERLOGGED)
                            && restored.getBlockState().getFluidState().getType() == Fluids.WATER,
                    "reload retains the actual source and waterlogged state");
            motor.generatedSpeed.setValue(96);
            motor.updateGeneratedRotation();
            helper.assertTrue(Math.abs(restored.getSpeed()) == 96 && Math.abs(output.getSpeed()) == 96,
                    "reloaded entity transmits a new native network speed, rather than only retaining saved RPM");
        }
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void nativeBracketsAttachPersistAndWrenchRefund(GameTestHelper helper) {
        List<BlockState> hosts = List.of(
                AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                AllBlocks.FLUID_PIPE.getDefaultState()
                        .setValue(BlockStateProperties.DOWN, false).setValue(BlockStateProperties.UP, false)
                        .setValue(BlockStateProperties.NORTH, false).setValue(BlockStateProperties.SOUTH, false)
                        .setValue(BlockStateProperties.WEST, true).setValue(BlockStateProperties.EAST, true));
        BracketBlock.BracketType[] types = {BracketBlock.BracketType.SHAFT, BracketBlock.BracketType.COG, BracketBlock.BracketType.PIPE};
        for (int i = 0; i < hosts.size(); i++) {
            BlockPos pos = new BlockPos(2 + i * 3, 3, 4);
            if (types[i] == BracketBlock.BracketType.PIPE) {
                // Keep the real neighbor-update path straight instead of relying on synthetic connection flags.
                var end = AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                helper.setBlock(pos.west(), end); helper.setBlock(pos.east(), end);
            }
            helper.setBlock(pos, hosts.get(i));
            if (types[i] == BracketBlock.BracketType.PIPE) {
                helper.assertTrue(FluidPropagator.getStraightPipeAxis(helper.getBlockState(pos)) == Direction.Axis.X,
                        "the connected native pipe is straight before bracket application: " + helper.getBlockState(pos));
            }
            SmartBlockEntity entity = helper.getBlockEntity(pos);
            entity.initialize();
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            var brackets = WeatheredContent.BRACKET.asStack(2);
            player.setItemInHand(InteractionHand.MAIN_HAND, brackets);
            var context = context(helper, pos, player, Direction.UP);
            helper.assertTrue(brackets.getItem().useOn(context).consumesAction() && brackets.getCount() == 1,
                    "native bracket item consumes exactly one decoration on " + types[i]);
            var behaviour = bracket(helper, pos);
            BlockState attached = behaviour.getBracket();
            helper.assertTrue(attached != null && attached.is(WeatheredContent.BRACKET.get())
                            && attached.getValue(BracketBlock.TYPE) == types[i]
                            && attached.getValue(BracketBlock.FACING) == Direction.UP
                            && attached.getValue(BracketBlock.AXIS_ALONG_FIRST_COORDINATE),
                    "Create selects the correct weathered bracket model and orientation for " + types[i]);
            brackets.getItem().useOn(context);
            helper.assertTrue(brackets.getCount() == 1 && behaviour.getBracket() == attached,
                    "applying the identical bracket twice neither consumes nor duplicates an item");
            var requirements = behaviour.getRequiredItems().getRequiredItems();
            helper.assertTrue(requirements.size() == 1 && requirements.getFirst().stack.is(WeatheredContent.BRACKET.asItem())
                            && requirements.getFirst().stack.getCount() == 1,
                    "native schematic requirements include exactly the attached weathered bracket");

            var saved = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
            entity.setRemoved();
            var restored = (SmartBlockEntity) entity.getType().create(entity.getBlockPos(), entity.getBlockState());
            restored.setLevel(helper.getLevel());
            restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
            helper.getLevel().setBlockEntity(restored);
            restored.initialize();
            helper.assertTrue(bracket(helper, pos).getBracket() == attached,
                    "native host NBT roundtrip retains the Core bracket and all orientation properties");
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            int before = player.getInventory().countItem(WeatheredContent.BRACKET.asItem());
            helper.assertTrue(player.getMainHandItem().getItem().useOn(context(helper, pos, player, Direction.UP)).consumesAction()
                            && !bracket(helper, pos).isBracketPresent()
                            && helper.getBlockState(pos).is(hosts.get(i).getBlock())
                            && player.getInventory().countItem(WeatheredContent.BRACKET.asItem()) == before + 1,
                    "native wrench removes only the decoration and refunds exactly one weathered bracket");
        }
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void wrenchEditsBothFlangesOfStackedGirders(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 3, 3);
        BlockState state = WeatheredContent.GIRDER.getDefaultState().setValue(GirderBlock.X, true)
                .setValue(GirderBlock.Z, false).setValue(GirderBlock.AXIS, Direction.Axis.X);
        helper.setBlock(pos, state); helper.setBlock(pos.above(), state);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var wrench = AllItems.WRENCH.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        var absolute = helper.absolutePos(pos);
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(.5, .99, .5), Direction.UP, absolute, false);
        BlockState initialLower = helper.getBlockState(pos), initialUpper = helper.getBlockState(pos.above());
        for (int click = 0; click < 2; click++) {
            BlockState lower = helper.getBlockState(pos), upper = helper.getBlockState(pos.above());
            var result = lower.useItemOn(wrench, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result.consumesAction()
                            && !helper.getBlockState(pos).getValue(GirderBlock.TOP).equals(lower.getValue(GirderBlock.TOP))
                            && !helper.getBlockState(pos.above()).getValue(GirderBlock.BOTTOM).equals(upper.getValue(GirderBlock.BOTTOM))
                            && helper.getBlockState(pos).getValue(GirderBlock.AXIS) == Direction.Axis.X
                            && helper.getBlockState(pos.above()).getValue(GirderBlock.AXIS) == Direction.Axis.X,
                    "a real wrench click toggles both existing flange states: before=" + lower + "/" + upper
                            + ", after=" + helper.getBlockState(pos) + "/" + helper.getBlockState(pos.above()));
        }
        helper.assertTrue(helper.getBlockState(pos) == initialLower && helper.getBlockState(pos.above()) == initialUpper,
                "two real wrench clicks restore the complete naturally connected girder states");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rotationPreservesEveryGirderAndShaftState(GameTestHelper helper) {
        for (BlockState state : WeatheredContent.GIRDER.get().getStateDefinition().getPossibleStates()) {
            BlockState quarter = state.rotate(Rotation.CLOCKWISE_90);
            Direction.Axis axis = state.getValue(GirderBlock.AXIS);
            Direction.Axis expectedAxis = axis == Direction.Axis.Y ? Direction.Axis.Y
                    : axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            helper.assertTrue(quarter.rotate(Rotation.COUNTERCLOCKWISE_90) == state,
                    "opposite quarter turns restore the complete girder state: " + state);
            helper.assertTrue(quarter.rotate(Rotation.CLOCKWISE_90) == state.rotate(Rotation.CLOCKWISE_180),
                    "two quarter turns equal a half turn for every girder state: " + state);
            helper.assertTrue(quarter.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90)
                            .rotate(Rotation.CLOCKWISE_90) == state,
                    "four quarter turns restore every girder state: " + state);
            helper.assertTrue(quarter.getValue(GirderBlock.X).equals(state.getValue(GirderBlock.Z))
                            && quarter.getValue(GirderBlock.Z).equals(state.getValue(GirderBlock.X))
                            && quarter.getValue(GirderBlock.AXIS) == expectedAxis
                            && quarter.getValue(GirderBlock.TOP).equals(state.getValue(GirderBlock.TOP))
                            && quarter.getValue(GirderBlock.BOTTOM).equals(state.getValue(GirderBlock.BOTTOM))
                            && quarter.getValue(BlockStateProperties.WATERLOGGED).equals(state.getValue(BlockStateProperties.WATERLOGGED)),
                    "quarter turns exchange horizontal beams while retaining water and vertical flanges: " + state);
        }
        for (BlockState state : WeatheredContent.GIRDER_SHAFT.get().getStateDefinition().getPossibleStates()) {
            Direction.Axis axis = state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS);
            for (Rotation rotation : Rotation.values()) {
                BlockState rotated = state.rotate(rotation);
                boolean quarterTurn = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
                Direction.Axis expectedAxis = quarterTurn
                        ? (axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : axis;
                helper.assertTrue(rotated.is(WeatheredContent.GIRDER_SHAFT.get())
                                && rotated.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) == expectedAxis
                                && rotated.getValue(GirderBlock.TOP).equals(state.getValue(GirderBlock.TOP))
                                && rotated.getValue(GirderBlock.BOTTOM).equals(state.getValue(GirderBlock.BOTTOM))
                                && rotated.getValue(BlockStateProperties.WATERLOGGED).equals(state.getValue(BlockStateProperties.WATERLOGGED)),
                        "native shaft rotation changes only its horizontal axis: " + rotation + "/" + state);
            }
        }
        helper.succeed();
    }

    private static BracketedBlockEntityBehaviour bracket(GameTestHelper helper, BlockPos pos) {
        var behaviour = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(pos), BracketedBlockEntityBehaviour.TYPE);
        helper.assertTrue(behaviour != null, "native host exposes Create bracket behavior");
        return behaviour;
    }

    private static net.minecraft.world.ItemInteractionResult useBlock(GameTestHelper helper, BlockPos pos,
            Player player, ItemStack stack, Direction face) {
        return helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), face, helper.absolutePos(pos), false));
    }

    private static UseOnContext context(GameTestHelper helper, BlockPos pos, Player player, Direction face) {
        var absolute = helper.absolutePos(pos);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(absolute), face, absolute, false));
    }
}
