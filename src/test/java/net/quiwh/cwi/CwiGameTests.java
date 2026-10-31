package net.quiwh.cwi;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.block.CwiBlocks;
import net.quiwh.cwi.block.DriedAlgaeBlock;
import net.quiwh.cwi.block.SpiderEggBlock;
import net.quiwh.cwi.processing.FanRecipes;

/** Execute in NeoForge's GameTest server, with this test source set and resources loaded. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class CwiGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void recipeCodec(GameTestHelper helper) {
        FanRecipeCodecTest.verify(helper.getLevel().registryAccess(), helper.getLevel());
        helper.assertTrue(BuiltInRegistries.RECIPE_TYPE.getKey(FanRecipes.CORRODING.recipeType())
                .equals(ResourceLocation.parse("cwi:corroding")), "corroding recipe registered");
        helper.assertTrue(BuiltInRegistries.RECIPE_TYPE.getKey(FanRecipes.STERILIZING.recipeType())
                .equals(ResourceLocation.parse("cwi:sterilzing")), "historical sterilzing recipe registered");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void blockContracts(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(CwiBlocks.DRIED_ALGAE.get())
                .equals(ResourceLocation.parse("cwi:dride_algae")), "historical algae block ID registered");
        helper.assertTrue(CwiBlocks.DRIED_ALGAE_ITEM.get() instanceof PlaceOnWaterBlockItem,
                "algae item supports placement on water");
        var algae = CwiBlocks.DRIED_ALGAE.get().defaultBlockState();
        helper.assertTrue(algae.getCollisionShape(helper.getLevel(), BlockPos.ZERO).isEmpty(),
                "algae allows entities through");
        helper.assertTrue(algae.getShape(helper.getLevel(), BlockPos.ZERO).max(net.minecraft.core.Direction.Axis.Y) == 1.0 / 16,
                "algae outline is one pixel high");
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var encoded = BlockState.CODEC.encodeStart(ops, algae).getOrThrow();
        helper.assertTrue(BlockState.CODEC.parse(ops, encoded).getOrThrow().getBlock() instanceof DriedAlgaeBlock,
                "serialized algae state preserves custom class");
        var egg = CwiBlocks.SPIDER_EGG.get().defaultBlockState().setValue(TurtleEggBlock.EGGS, 3);
        var eggJson = BlockState.CODEC.encodeStart(ops, egg).getOrThrow();
        var decodedEgg = BlockState.CODEC.parse(ops, eggJson).getOrThrow();
        helper.assertTrue(decodedEgg.getBlock() instanceof SpiderEggBlock && decodedEgg.getValue(TurtleEggBlock.EGGS) == 3,
                "serialized egg state preserves custom class and egg count");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void spiderSpawnsOncePerPlacement(GameTestHelper helper) {
        helper.killAllEntities();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos.below(), Blocks.SAND);
        helper.setBlock(pos, CwiBlocks.SPIDER_EGG.get());
        helper.assertTrue(helper.getEntities(EntityType.SPIDER).size() == 1, "one spider from initial placement");
        helper.setBlock(pos, CwiBlocks.SPIDER_EGG.get().defaultBlockState().setValue(TurtleEggBlock.HATCH, 1));
        helper.assertTrue(helper.getEntities(EntityType.SPIDER).size() == 1, "hatch-state update adds no spider");
        helper.setBlock(pos, CwiBlocks.SPIDER_EGG.get().defaultBlockState().setValue(TurtleEggBlock.EGGS, 2));
        helper.assertTrue(helper.getEntities(EntityType.SPIDER).size() == 1, "egg-count update adds no spider");
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, CwiBlocks.SPIDER_EGG.get());
        helper.assertTrue(helper.getEntities(EntityType.SPIDER).size() == 2, "a replacement placement adds one spider");
        helper.succeed();
    }
}
