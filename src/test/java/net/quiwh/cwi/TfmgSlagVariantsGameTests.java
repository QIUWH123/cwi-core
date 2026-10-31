package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class TfmgSlagVariantsGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void slagVariantsKeepOldIdsShapesAndDrops(GameTestHelper helper) {
        if (!hasTfmg(helper)) return;
        Block base = block("slag_bricks");
        for (String variant : new String[]{"slab", "stairs", "wall"}) {
            var id = id("slag_bricks_" + variant);
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "restored block " + id);
            Block block = BuiltInRegistries.BLOCK.get(id);
            helper.assertTrue(block.asItem() != Items.AIR && block.asItem() == BuiltInRegistries.ITEM.get(id),
                    "restored matching block item " + id);
            helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "pickaxe tag " + id);
            helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(block.defaultBlockState())
                            && !new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()),
                    "old stone-tier mining requirement " + id);
            helper.assertTrue(block.defaultBlockState().requiresCorrectToolForDrops(), "correct-tool drops " + id);
            var wet = block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
            helper.assertTrue(wet.getFluidState().getType() == Fluids.WATER, "waterlogging " + id);
            helper.assertTrue(wet.getSoundType() == base.defaultBlockState().getSoundType(), "slag sound " + id);
            var pos = helper.absolutePos(new BlockPos(2, 2, 2));
            var drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), pos, null,
                    helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                    "survival drop " + id);
            helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("cwi", id.getPath())),
                    "no duplicate cwi namespace " + id);
        }
        var slab = block("slag_bricks_slab");
        helper.assertTrue(slab instanceof SlabBlock, "native slab behavior");
        helper.assertTrue(block("slag_bricks_stairs") instanceof StairBlock, "native stair behavior");
        helper.assertTrue(block("slag_bricks_wall") instanceof WallBlock, "native wall behavior");
        var single = slab.defaultBlockState();
        helper.assertTrue(single.getShape(helper.getLevel(), BlockPos.ZERO).bounds().maxY == .5, "half slab shape");
        var doubled = single.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        var drops = Block.getDrops(doubled, helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), null,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(slab.asItem()) && drops.getFirst().getCount() == 2,
                "breaking a double slab returns both halves");
        var wallPos = new BlockPos(4, 2, 4);
        helper.setBlock(wallPos, block("slag_bricks_wall"));
        helper.setBlock(wallPos.relative(Direction.NORTH), block("slag_bricks_wall"));
        helper.assertTrue(helper.getBlockState(wallPos).getValue(WallBlock.NORTH_WALL) != WallSide.NONE,
                "walls connect to neighboring walls");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void slagVariantsHaveStonecuttingAndCreativeEntries(GameTestHelper helper) {
        if (!hasTfmg(helper)) return;
        var input = new SingleRecipeInput(new ItemStack(block("slag_bricks")));
        for (String variant : new String[]{"slab", "stairs", "wall"}) {
            var recipeId = id("slag_bricks_" + variant + "_from_slag_bricks_stonecutting");
            var recipe = helper.getLevel().getRecipeManager().byKey(recipeId).orElseThrow().value();
            helper.assertTrue(recipe instanceof StonecutterRecipe, "native stonecutting recipe " + recipeId);
            var cutting = (StonecutterRecipe) recipe;
            helper.assertTrue(cutting.matches(input, helper.getLevel()), "slag bricks accepted " + recipeId);
            var result = cutting.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(block("slag_bricks_" + variant).asItem()) && result.getCount() == (variant.equals("slab") ? 2 : 1),
                    "old stonecutting yields " + recipeId);
        }
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id("tfmg_decoration"));
        for (int rebuild = 0; rebuild < 2; rebuild++) {
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
            for (String variant : new String[]{"slab", "stairs", "wall"}) {
                var item = block("slag_bricks_" + variant).asItem();
                helper.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1,
                        "one decoration-tab entry after rebuild " + rebuild + ": " + variant);
            }
        }
        helper.succeed();
    }

    private static boolean hasTfmg(GameTestHelper helper) {
        if (ModList.get().isLoaded("tfmg")) return true;
        for (String variant : new String[]{"slab", "stairs", "wall"}) {
            var id = id("slag_bricks_" + variant);
            helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(id) && !BuiltInRegistries.ITEM.containsKey(id),
                    "no orphan TFMG variant when TFMG is absent: " + id);
        }
        helper.succeed();
        return false;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tfmg", path);
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(id(path));
    }
}
