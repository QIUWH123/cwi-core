package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.MachineBlock;
import net.quiwh.cwi.machine.MachineBlocks;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class FurnaceCapabilityGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rotationInvalidatesCachedSides(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MachineBlocks.BLAST_FURNACE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var north = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
        var east = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, helper.getLevel(), helper.absolutePos(pos), Direction.EAST);
        helper.assertTrue(north.getCapability() != null && east.getCapability() == null, "initial cached molten output faces north");
        var state = helper.getBlockState(pos);
        helper.setBlock(pos, state.rotate(Rotation.CLOCKWISE_90));
        helper.assertTrue(north.getCapability() == null && east.getCapability() != null,
                "wrench-style rotation invalidates both present and absent side caches");
        helper.setBlock(pos, state);
        helper.assertTrue(north.getCapability() != null && east.getCapability() == null,
                "rotating back restores the original cached output side");
        helper.succeed();
    }
}
