package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.interaction.InteractionRecipe;
import net.quiwh.cwi.interaction.InteractionRecipes;
import net.quiwh.cwi.interaction.WorldSpouting;

/** Native recipe replacement must update cached rules without depending on a tag reload. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class InteractionCacheGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void spoutingUsesReplacedAndRemovedRulesImmediately(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        var id = ResourceLocation.parse("cwi:contracts/cache_spouting");
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var oldBlock = level.getBlockState(pos);
        try {
            InteractionRecipes.invalidate();
            install(manager, saved, rule(helper, id, 250, "minecraft:emerald_block"));
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.DIAMOND_BLOCK);
            var water = new FluidStack(Fluids.WATER, 1000);
            helper.assertTrue(WorldSpouting.fill(level, pos, water, true) == 250, "initial rule uses 250mB");

            install(manager, saved, rule(helper, id, 500, "minecraft:gold_block"));
            helper.assertTrue(WorldSpouting.fill(level, pos, water, true) == 500,
                    "same-ID replacement uses the new 500mB rule without a tag update");
            helper.assertTrue(WorldSpouting.fill(level, pos, water, false) == 500
                    && level.getBlockState(pos).is(Blocks.GOLD_BLOCK), "execution uses the replacement output");

            manager.replaceRecipes(saved);
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.DIAMOND_BLOCK);
            helper.assertTrue(!WorldSpouting.hasRules(level, pos) && WorldSpouting.fill(level, pos, water, true) == -1,
                    "removed rules immediately return control to Create");

            install(manager, saved, rule(helper, id, 750, "minecraft:iron_block"));
            helper.assertTrue(WorldSpouting.fill(level, pos, water, true) == 750,
                    "adding a rule invalidates a cached negative lookup");
        } finally {
            manager.replaceRecipes(saved);
            InteractionRecipes.invalidate();
            level.setBlockAndUpdate(pos, oldBlock);
        }
        helper.succeed();
    }

    private static RecipeHolder<InteractionRecipe> rule(GameTestHelper helper, ResourceLocation id, int amount, String output) {
        var json = JsonParser.parseString("""
                {"input":"minecraft:diamond_block","fluid":{"fluid":"minecraft:water","amount":%d},"outputs":["%s"]}
                """.formatted(amount, output));
        var recipe = InteractionRecipes.Kind.BLOCK_SPOUTING.serializer().codec().codec()
                .parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        return new RecipeHolder<>(id, recipe);
    }

    private static void install(RecipeManager manager, List<RecipeHolder<?>> saved, RecipeHolder<InteractionRecipe> rule) {
        var recipes = new ArrayList<RecipeHolder<?>>(saved);
        recipes.add(rule);
        manager.replaceRecipes(recipes);
    }
}
