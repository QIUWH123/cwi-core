package net.quiwh.cwi;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.hammer.HammerRecipe;
import net.quiwh.cwi.hammer.HammerRecipes;

/** Native codec and lookup contract for the CWI hammer recipe builders. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class HammerRecipeCodecTest {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void hammerRecipes(GameTestHelper helper) {
        verify(helper.getLevel().registryAccess());
        helper.succeed();
    }

    public static void verify(RegistryAccess registries) {
        var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var block = decode(HammerRecipes.BLOCK_CONVERSION, registries, """
                {"input":{"item":"minecraft:smooth_basalt"},
                 "target":{"id":"minecraft:stone"},
                 "drops":[{"stack":{"id":"minecraft:gravel","count":4},"chance":0.75}],
                 "stage":2}
                """);
        check(block.input().test(new ItemStack(Items.SMOOTH_BASALT)), "block input matches");
        check(!block.input().test(new ItemStack(Items.OBSIDIAN)), "block input rejects other item");
        check(block.target().is(Items.STONE) && block.stage() == 2, "block target and stage preserved");
        check(block.getIngredients().size() == 1
                        && block.getIngredients().getFirst().test(new ItemStack(Items.SMOOTH_BASALT)),
                "hammer recipes expose exactly one input ingredient");
        check(block.drops().size() == 1 && block.drops().getFirst().stack().getCount() == 4
                && block.drops().getFirst().chance() == 0.75F, "block drop count/chance preserved");
        var blockJson = HammerRecipes.BLOCK_CONVERSION.serializer().codec().codec().encodeStart(ops, block).getOrThrow();
        check(HammerRecipes.BLOCK_CONVERSION.serializer().codec().codec().parse(ops, blockJson).getOrThrow().stage() == 2,
                "block JSON round trip");
        var depot = decode(HammerRecipes.DEPOT_CONVERSION, registries, """
                {"input":{"item":"minecraft:raw_iron"},"target":{"id":"minecraft:iron_ingot"},"stage":3}
                """);
        check(depot.input().test(new ItemStack(Items.RAW_IRON)), "depot input matches");
        check(depot.target().is(Items.IRON_INGOT) && depot.stage() == 3, "depot target and stage preserved");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            HammerRecipes.BLOCK_CONVERSION.serializer().streamCodec().encode(buffer, block);
            var copy = HammerRecipes.BLOCK_CONVERSION.serializer().streamCodec().decode(buffer);
            check(copy.target().is(Items.STONE) && copy.drops().getFirst().chance() == 0.75F,
                    "block network round trip");
            check(buffer.readableBytes() == 0, "block network has no trailing bytes");
        } finally {
            buffer.release();
        }
        reject(HammerRecipes.BLOCK_CONVERSION, registries,
                "{\"input\":{\"item\":\"minecraft:smooth_basalt\"},\"target\":{\"id\":\"minecraft:stick\"}}");
        reject(HammerRecipes.DEPOT_CONVERSION, registries,
                "{\"input\":{\"item\":\"minecraft:raw_iron\"},\"target\":{\"id\":\"minecraft:air\"}}");
        reject(HammerRecipes.BLOCK_CONVERSION, registries,
                "{\"input\":{\"item\":\"minecraft:smooth_basalt\"},\"stage\":0}");
        reject(HammerRecipes.BLOCK_CONVERSION, registries,
                "{\"input\":{\"item\":\"minecraft:smooth_basalt\"},\"drops\":[{\"stack\":{\"id\":\"minecraft:gravel\"},\"chance\":1.1}]}");
    }

    private static HammerRecipe decode(HammerRecipes.Entry entry, RegistryAccess registries, String json) {
        return entry.serializer().codec().codec().parse(
                registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                JsonParser.parseString(json)).getOrThrow();
    }

    private static void reject(HammerRecipes.Entry entry, RegistryAccess registries, String json) {
        check(entry.serializer().codec().codec().parse(
                registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                JsonParser.parseString(json)).isError(), "invalid hammer recipe accepted: " + json);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

}
