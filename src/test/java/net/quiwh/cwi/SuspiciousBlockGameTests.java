package net.quiwh.cwi;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class SuspiciousBlockGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void suspiciousBlocksConvertToTheirDirtVariants(GameTestHelper helper) {
        assertBrushable(helper, "suspicious_depleted_dirt", "depleted_dirt");
        assertBrushable(helper, "suspicious_ash_dirt", "ash_dirt");
        helper.succeed();
    }

    private static void assertBrushable(GameTestHelper helper, String suspiciousId, String resultId) {
        Block suspicious = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(CwiCore.ID, suspiciousId));
        Block result = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(CwiCore.ID, resultId));
        helper.assertTrue(suspicious instanceof BrushableBlock,
                suspiciousId + " must use BrushableBlock");
        helper.assertTrue(BlockEntityType.BRUSHABLE_BLOCK.isValid(suspicious.defaultBlockState()),
                suspiciousId + " must be valid for the vanilla brushable block entity type");
        helper.assertTrue(((BrushableBlock) suspicious).getTurnsInto() == result,
                suspiciousId + " must brush into " + resultId);
    }
}
