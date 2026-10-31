package net.quiwh.cwi;

import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.MachineBlocks;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class MachineRenderGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void geometryKeepsNeighborFaces(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        verifyRenderProperties(state -> {
            helper.getLevel().setBlockAndUpdate(pos, state);
            return helper.getLevel();
        }, pos);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void internalPartsReceiveWorldLight(GameTestHelper helper) {
        BlockPos incubator = new BlockPos(1, 2, 1);
        BlockPos mixer = new BlockPos(1, 2, 3);
        helper.setBlock(incubator, MachineBlocks.INCUBATOR.get());
        helper.setBlock(mixer, MachineBlocks.MIXING_VESSEL.get());
        helper.setBlock(incubator.east(), Blocks.SEA_LANTERN);
        helper.setBlock(mixer.east(), Blocks.SEA_LANTERN);
        helper.runAfterDelay(10, () -> {
            for (BlockPos pos : List.of(incubator, mixer)) {
                int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(pos));
                helper.assertTrue(light >= 14,
                        "The machine's own position supplies real world light to its rotating parts: " + light);
            }
            helper.succeed();
        });
    }

    public static int verifyRenderProperties(Function<BlockState, BlockGetter> levelForState, BlockPos pos) {
        int checks = 0;
        List<Block> machines = List.of(MachineBlocks.BLAST_FURNACE.get(), MachineBlocks.FURNACE_HATCH.get(),
                MachineBlocks.INCUBATOR.get(), MachineBlocks.MIXING_VESSEL.get(), MachineBlocks.STIRLING_ENGINE.get(),
                MachineBlocks.LAVA_EXTRACTOR_HEAD.get(), MachineBlocks.LAVA_EXTRACTOR_PUMP.get(),
                MachineBlocks.LAVA_EXTRACTOR_STRESS_INPUT.get(), MachineBlocks.LAVA_EXTRACTOR_PUMP_BLADE.get(),
                MachineBlocks.SOLAR_EVAPORATOR.get());
        for (Block block : machines) for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BlockGetter level = levelForState.apply(state);
            require(!state.canOcclude(), "Visible gaps must not hide neighboring faces: " + state); checks++;
            require(!state.isSolidRender(level, pos), "Cutout machine must not be treated as opaque: " + state); checks++;
            require(state.getLightBlock(level, pos) <= 1, "Machine must not extinguish its internal part lighting: " + state); checks++;
            for (Direction side : Direction.values()) {
                require(Block.shouldRenderFace(Blocks.STONE.defaultBlockState(), level, pos.relative(side), side.getOpposite(), pos),
                        "Neighbor stone face must render through machine gaps: " + state + " " + side); checks++;
            }
        }
        for (Block block : List.of(MachineBlocks.INCUBATOR.get(), MachineBlocks.MIXING_VESSEL.get())) {
            BlockState state = block.defaultBlockState();
            require(state.isCollisionShapeFullBlock(levelForState.apply(state), pos),
                    "Rendering correction must retain the original collision cube: " + state); checks++;
        }
        return checks;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
