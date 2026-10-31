package net.quiwh.cwi;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Test.js must reach the native serializers, including after the real server reload. */
@GameTestHolder("cwi")
@PrefixGameTestTemplate(false)
public final class OptionalSchemaGameTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void testScriptHammerActions(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.CHISELED_QUARTZ_BLOCK);
        check(net.quiwh.cwi.hammer.HammerActions.applyBlockConversions(helper.getLevel(), helper.absolutePos(pos), 1) == 1
                && helper.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK), "Test.js stage-one hammer conversion");
        check(net.quiwh.cwi.hammer.HammerActions.applyBlockConversions(helper.getLevel(), helper.absolutePos(pos), 2) == 1
                && helper.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.QUARTZ_BRICKS), "Test.js stage-two hammer conversion");
        helper.setBlock(pos, com.simibubi.create.AllBlocks.DEPOT.get());
        com.simibubi.create.content.logistics.depot.DepotBlockEntity depot = helper.getBlockEntity(pos); depot.initialize();
        depot.setHeldItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.QUARTZ_PILLAR));
        check(net.quiwh.cwi.hammer.HammerActions.applyDepotConversion(helper.getLevel(), helper.absolutePos(pos), 1)
                && depot.getHeldItem().is(net.minecraft.world.item.Items.QUARTZ) && depot.getHeldItem().getCount() == 2, "Test.js counted depot output");
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void testScriptRecipesDecodeAndSynchronize(GameTestHelper helper) {
        verify(helper.getLevel());
        helper.succeed();
    }

    public static List<String> verify(Level level) {
        verifyReplacements(level);
        var paths = new ArrayList<>(List.of("hammer_block_1", "hammer_block_2", "hammer_depot_1", "hammer_depot_2",
                "furnace_1", "furnace_2", "incubator_1", "incubator_2", "mixer_1", "mixer_2", "smithing_empty", "smithing_normal"));
        add(paths, "createaddition", "rolling", "charging", "liquid_burning");
        add(paths, "createdieselgenerators", "hammering", "wire_cutting", "diesel_distillation", "basin_fermenting", "bulk_fermenting");
        add(paths, "tfmg", "coking", "tfmg_distillation", "vat");
        add(paths, "ratatouille", "threshing", "demolding");
        add(paths, "lychee", "item_inside", "item_burning", "block_interacting", "dripstone");
        add(paths, "vintageimprovements", "pressurizing", "leaves_vibrating", "auto_upgrade", "auto_smithing");
        add(paths, "createmetallurgy", "entity_melting", "melting", "alloying");
        add(paths, "petrochem", "pumpjack");
        var encoded = new ArrayList<String>();
        for (String path : paths) {
            var id = ResourceLocation.parse("cwi:test/" + path);
            var recipe = level.getRecipeManager().byKey(id).orElseThrow(() -> new AssertionError("Test.js recipe missing: " + id)).value();
            @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>) recipe.getSerializer();
            var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var json = serializer.codec().codec().encodeStart(ops, recipe).getOrThrow();
            var decoded = serializer.codec().codec().parse(ops, json).getOrThrow();
            check(serializer.codec().codec().encodeStart(ops, decoded).getOrThrow().equals(json), "codec roundtrip: " + id
                    + " before=" + json + " after=" + serializer.codec().codec().encodeStart(ops, decoded).getOrThrow());
            for (int cycle = 0; cycle < 3; cycle++) {
                decoded = serializer.codec().codec().parse(ops, serializer.codec().codec().encodeStart(ops, decoded).getOrThrow()).getOrThrow();
                check(serializer.codec().codec().encodeStart(ops, decoded).getOrThrow().equals(json), "repeated codec roundtrip: " + id);
            }
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                serializer.streamCodec().encode(buffer, recipe);
                var copy = serializer.streamCodec().decode(buffer);
                check(buffer.readableBytes() == 0, "network trailing bytes: " + id);
                if (recipe instanceof com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> original
                        && copy instanceof com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> networkCopy)
                    verifyProcessing(original, networkCopy, id.toString());
                else check(serializer.codec().codec().encodeStart(ops, copy).getOrThrow().equals(json), "network roundtrip: " + id);
            } finally { buffer.release(); }
            encoded.add(id + "=" + json);
        }
        return encoded;
    }

    private static void verifyReplacements(Level level) {
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        if (ModList.get().isLoaded("createaddition")) {
            var recipe = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) level.getRecipeManager()
                    .byKey(ResourceLocation.parse("cwi:schema_contracts/rolling_replace")).orElseThrow().value();
            check(recipe.getIngredients().getFirst().test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FLINT)), "typed addon input replacement");
            var result = recipe.getRollableResults().getFirst();
            check(result.getStack().is(net.minecraft.world.item.Items.IRON_NUGGET) && result.getStack().getCount() == 3 && result.getChance() == 0.25F,
                    "typed addon replacement retains count/chance");
        }
        if (ModList.get().isLoaded("tfmg")) {
            var recipe = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) level.getRecipeManager()
                    .byKey(ResourceLocation.parse("cwi:schema_contracts/distillation_replace")).orElseThrow().value();
            check(recipe.getFluidIngredients().getFirst().amount() == 125 && recipe.getFluidIngredients().getFirst().test(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 125)), "fluid input replacement preserves amount");
            check(recipe.getFluidResults().getFirst().is(net.minecraft.world.level.material.Fluids.WATER) && recipe.getFluidResults().getFirst().getAmount() == 75, "fluid output replacement preserves amount");
        }
        if (ModList.get().isLoaded("lychee")) {
            var recipe = level.getRecipeManager().byKey(ResourceLocation.parse("cwi:schema_contracts/lychee_replace")).orElseThrow().value();
            @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>) recipe.getSerializer();
            var json = serializer.codec().codec().encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
            var input = json.getAsJsonArray("item_in").get(0).getAsJsonObject();
            check(input.get("item").getAsString().equals("minecraft:flint") && input.get("count").getAsInt() == 3, "Lychee counted input replacement");
            var branch = json.getAsJsonArray("post").get(0).getAsJsonObject();
            var then = branch.getAsJsonArray("then").get(0).getAsJsonObject();
            var otherwise = branch.getAsJsonArray("else").get(0).getAsJsonObject();
            check(then.get("id").getAsString().equals("minecraft:iron_nugget") && then.get("count").getAsInt() == 4
                    && otherwise.get("id").getAsString().equals("minecraft:iron_nugget") && otherwise.get("count").getAsInt() == 2,
                    "nested Lychee output replacement preserves counts in both branches");
            check(branch.toString().contains("0.5") && then.toString().contains("0.25"), "Lychee replacement retains native conditions");
        }
    }

    private static void verifyProcessing(com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> a,
            com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> b, String id) {
        check(a.getIngredients().size() == b.getIngredients().size() && a.getFluidIngredients().size() == b.getFluidIngredients().size(), "network input sizes: " + id);
        for (int i = 0; i < a.getIngredients().size(); i++) {
            // Native Ingredient streams resolve tags into item sets; compare their meaning, not JSON notation.
            var expected = java.util.Arrays.stream(a.getIngredients().get(i).getItems()).map(stack -> stack.getItem()).collect(java.util.stream.Collectors.toSet());
            var actual = java.util.Arrays.stream(b.getIngredients().get(i).getItems()).map(stack -> stack.getItem()).collect(java.util.stream.Collectors.toSet());
            check(expected.equals(actual), "network item ingredient: " + id);
        }
        for (int i = 0; i < a.getFluidIngredients().size(); i++) {
            var x = a.getFluidIngredients().get(i); var y = b.getFluidIngredients().get(i);
            check(x.amount() == y.amount() && java.util.Arrays.stream(x.getFluids()).allMatch(y.ingredient()::test)
                    && java.util.Arrays.stream(y.getFluids()).allMatch(x.ingredient()::test), "network fluid ingredient: " + id);
        }
        check(a.getRollableResults().size() == b.getRollableResults().size() && a.getFluidResults().size() == b.getFluidResults().size(), "network output sizes: " + id);
        for (int i = 0; i < a.getRollableResults().size(); i++) {
            var x = a.getRollableResults().get(i); var y = b.getRollableResults().get(i);
            check(net.minecraft.world.item.ItemStack.matches(x.getStack(), y.getStack()) && x.getChance() == y.getChance(), "network counted/chance result: " + id);
        }
        for (int i = 0; i < a.getFluidResults().size(); i++) check(net.neoforged.neoforge.fluids.FluidStack.matches(a.getFluidResults().get(i), b.getFluidResults().get(i)), "network fluid result: " + id);
        check(a.getProcessingDuration() == b.getProcessingDuration() && a.getRequiredHeat() == b.getRequiredHeat(), "network heat/time: " + id);
    }

    private static void add(List<String> paths, String mod, String... types) {
        if (!ModList.get().isLoaded(mod)) return;
        for (var type : types) for (int i = 1; i <= 2; i++) paths.add(type + "_" + i);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
