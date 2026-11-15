package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.quiwh.cwi.block.InfectedBuddingBlock;

import java.util.List;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class BuddingBlocksGameTests {
    private static final List<ResourceLocation> QUARTZ_STAGES = List.of(
            id("cwi:small_quartz_bud"),
            id("cwi:medium_quartz_bud"),
            id("cwi:large_quartz_bud"),
            id("cwi:quartz_crystal"));

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void infectedBlocksAreRegisteredAndGrowAmethyst(GameTestHelper helper) {
        Block amethyst = block("infected_budding_amethyst");
        Block quartz = block("infected_budding_quartz");

        helper.assertTrue(amethyst instanceof InfectedBuddingBlock, "infected amethyst uses native budding behavior");
        helper.assertTrue(amethyst.asItem() == BuiltInRegistries.ITEM.get(id("cwi:infected_budding_amethyst")),
                "infected amethyst has a matching block item");
        helper.assertTrue(amethyst.defaultBlockState().isRandomlyTicking(), "infected amethyst is randomly ticking");
        helper.assertTrue(amethyst.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "infected amethyst is pickaxe mineable");
        helper.assertTrue(amethyst.defaultBlockState().is(BlockTags.NEEDS_IRON_TOOL),
                "infected amethyst needs an iron tool");

        helper.assertTrue(quartz instanceof InfectedBuddingBlock, "infected quartz uses native budding behavior");
        helper.assertTrue(quartz.asItem() == BuiltInRegistries.ITEM.get(id("cwi:infected_budding_quartz")),
                "infected quartz has a matching block item");
        helper.assertTrue(quartz.defaultBlockState().isRandomlyTicking(), "infected quartz is randomly ticking");
        helper.assertTrue(quartz.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "infected quartz is pickaxe mineable");
        helper.assertTrue(quartz.defaultBlockState().is(BlockTags.NEEDS_IRON_TOOL),
                "infected quartz needs an iron tool");

        ServerLevel level = helper.getLevel();
        BlockPos relativePos = new BlockPos(1, 1, 1);
        BlockPos sourcePos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos, amethyst);
        InfectedBuddingBlock infected = (InfectedBuddingBlock) amethyst;
        for (int seed = 0; seed < 128 && !hasAmethystGrowth(level, sourcePos); seed++) {
            infected.randomTick(level.getBlockState(sourcePos), level, sourcePos, RandomSource.create(seed));
        }
        helper.assertTrue(hasAmethystGrowth(level, sourcePos), "infected amethyst grows an adjacent stage");

        if (QUARTZ_STAGES.stream().allMatch(BuiltInRegistries.BLOCK::containsKey)) {
            BlockPos quartzPos = helper.absolutePos(new BlockPos(4, 1, 1));
            helper.setBlock(new BlockPos(4, 1, 1), quartz);
            InfectedBuddingBlock infectedQuartz = (InfectedBuddingBlock) quartz;
            for (int seed = 0; seed < 128 && !hasQuartzGrowth(level, quartzPos); seed++) {
                infectedQuartz.randomTick(level.getBlockState(quartzPos), level, quartzPos, RandomSource.create(seed));
            }
            helper.assertTrue(hasQuartzGrowth(level, quartzPos), "infected quartz grows an adjacent stage");
        }
        helper.succeed();
    }

    private static boolean hasAmethystGrowth(ServerLevel level, BlockPos sourcePos) {
        for (Direction direction : Direction.values()) {
            BlockState state = level.getBlockState(sourcePos.relative(direction));
            if (state.is(Blocks.SMALL_AMETHYST_BUD)
                    || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                    || state.is(Blocks.LARGE_AMETHYST_BUD)
                    || state.is(Blocks.AMETHYST_CLUSTER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasQuartzGrowth(ServerLevel level, BlockPos sourcePos) {
        for (Direction direction : Direction.values()) {
            BlockState state = level.getBlockState(sourcePos.relative(direction));
            if (QUARTZ_STAGES.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                return true;
            }
        }
        return false;
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(id("cwi:" + path));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
