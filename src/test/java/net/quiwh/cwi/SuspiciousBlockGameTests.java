package net.quiwh.cwi;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class SuspiciousBlockGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void depletedDirtBrushingUsesPackBlock(GameTestHelper helper) {
        assertBrushable(helper, "suspicious_depleted_dirt", "depleted_dirt");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void ashDirtBrushingUsesPackBlock(GameTestHelper helper) {
        assertBrushable(helper, "suspicious_ash_dirt", "ash_dirt");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void ordinaryDirtIsNotRegisteredByCore(GameTestHelper helper) {
        for (String id : new String[]{"depleted_dirt", "ash_dirt"}) {
            ResourceLocation coreId = ResourceLocation.fromNamespaceAndPath(CwiCore.ID, id);
            helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(coreId),
                    "Core must not duplicate pack block " + coreId);
            helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(coreId),
                    "Core must not duplicate pack item " + coreId);
        }
        helper.succeed();
    }

    private static void assertBrushable(GameTestHelper helper, String suspiciousId, String resultId) {
        Block suspicious = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(CwiCore.ID, suspiciousId));
        ResourceLocation packId = ResourceLocation.fromNamespaceAndPath("kubejs", resultId);
        Block result = BuiltInRegistries.BLOCK.getOptional(packId).orElseThrow();
        helper.assertTrue(suspicious instanceof BrushableBlock,
                suspiciousId + " must use BrushableBlock");
        helper.assertTrue(BlockEntityType.BRUSHABLE_BLOCK.isValid(suspicious.defaultBlockState()),
                suspiciousId + " must be valid for the vanilla brushable block entity type");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, suspicious.defaultBlockState());
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof BrushableBlockEntity,
                suspiciousId + " must create the vanilla brushable block entity");
        BrushableBlockEntity brushable = (BrushableBlockEntity) helper.getLevel().getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        long now = helper.getLevel().getGameTime();
        for (int i = 0; i < 10; i++) {
            helper.assertTrue(brushable.brush(now + i * 10L, player, Direction.UP) == (i == 9),
                    suspiciousId + " must finish only on the tenth brush");
        }
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(result),
                suspiciousId + " must brush into " + packId);
    }
}
