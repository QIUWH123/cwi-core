package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import snownee.lychee.RecipeTypes;
import snownee.lychee.recipes.BlockInteractingRecipe;

/** Native empty-map ingredients must survive KubeJS and still reject occupied interaction hands. */
@PrefixGameTestTemplate(false)
public final class LycheeEmptyHandGameTests {
    private static final ResourceLocation SINGLE = ResourceLocation.parse("cwi:schema_contracts/lychee_empty_hand");
    private static final ResourceLocation DOUBLE = ResourceLocation.parse("cwi:schema_contracts/lychee_empty_primary");

    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void emptyHandsDecodeRoundtripAndInteract(GameTestHelper helper) {
        verify(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.succeed();
    }

    public static void verifySchemaJson(KubeRecipe recipe) {
        recipe.serialize();
        var encoded = recipe.json.deepCopy();
        var input = encoded.get("item_in");
        var empty = input.isJsonArray() ? input.getAsJsonArray().get(0) : input;
        check(empty.isJsonObject() && empty.getAsJsonObject().isEmpty(), "KubeJS encodes empty hands as native {}: " + encoded);
        var decoded = (BlockInteractingRecipe) Recipe.CODEC.parse(recipe.type.event.ops.json(), encoded).getOrThrow();
        check(decoded.sizedIngredients().getFirst().ingredient().isEmpty(), "native decoder accepts KubeJS empty-hand JSON");
        recipe.deserialize(false);
        recipe.serialize();
        check(encoded.equals(recipe.json), "KubeJS empty-hand JSON roundtrip retains all properties");
    }

    public static List<String> verify(ServerLevel level, BlockPos pos) {
        var evidence = new ArrayList<String>();
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var nativeJson = JsonParser.parseString("""
                {"item_in":{},"block_in":"minecraft:lime_concrete","contextual":{"type":"is_sneaking"},
                "post":[{"type":"place","block":"minecraft:yellow_concrete"}]}
                """);
        var nativeRecipe = BlockInteractingRecipe.Serializer.CODEC.codec().parse(ops, nativeJson).getOrThrow();
        check(nativeRecipe.sizedIngredients().getFirst().ingredient().isEmpty(), "native {} denotes empty hand");
        evidence.add("native_empty_map_decodes");
        var manager = level.getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var state = level.getBlockState(pos);
        var player = FakePlayerFactory.getMinecraft(level);
        var main = player.getMainHandItem();
        var off = player.getOffhandItem();
        boolean sneaking = player.isShiftKeyDown();
        try {
            var recipes = new ArrayList<RecipeHolder<?>>(original);
            recipes.add(new RecipeHolder<>(ResourceLocation.parse("cwi:empty_hand_native_control"), nativeRecipe));
            manager.replaceRecipes(recipes);
            refresh();
            assertInteraction(level, pos, player, false, ItemStack.EMPTY, ItemStack.EMPTY, false);
            assertInteraction(level, pos, player, true, new ItemStack(Items.FLINT), ItemStack.EMPTY, false);
            assertInteraction(level, pos, player, true, ItemStack.EMPTY, ItemStack.EMPTY, true);
            assertInteraction(level, pos, player, true, ItemStack.EMPTY, new ItemStack(Items.FLINT), true);
            evidence.add("native_sneaking_empty_hand_only_interaction");
            for (var id : List.of(SINGLE, DOUBLE)) {
                var recipe = (BlockInteractingRecipe) manager.byKey(id)
                        .orElseThrow(() -> new AssertionError("KubeJS empty-hand recipe missing: " + id)).value();
                check(recipe.sizedIngredients().getFirst().ingredient().isEmpty(), "KubeJS retained empty ingredient: " + id);
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                try {
                    BlockInteractingRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
                    var copy = BlockInteractingRecipe.Serializer.STREAM_CODEC.decode(buffer);
                    check(buffer.readableBytes() == 0 && copy.sizedIngredients().getFirst().ingredient().isEmpty(), "network retained empty hand: " + id);
                } finally { buffer.release(); }
                check(recipe.sizedIngredients().size() == (id.equals(SINGLE) ? 1 : 2), "one/two hand input arity: " + id);
                evidence.add("schema_network_roundtrip:" + id);
            }
            // The KubeJS single-hand contract uses cyan concrete, isolated from the native control.
            assertSchemaInteraction(level, pos, player, true, new ItemStack(Items.FLINT), false);
            assertSchemaInteraction(level, pos, player, false, ItemStack.EMPTY, false);
            assertSchemaInteraction(level, pos, player, true, ItemStack.EMPTY, true);
            evidence.add("schema_sneaking_empty_hand_only_interaction");
            for (var offhand : List.of(ItemStack.EMPTY, new ItemStack(Items.FLINT, 1), new ItemStack(Items.FLINT, 2))) {
                level.setBlock(pos, Blocks.MAGENTA_CONCRETE.defaultBlockState(), 3);
                player.setShiftKeyDown(true);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, offhand);
                boolean expected = offhand.getCount() == 2;
                var result = BlockInteractingRecipe.invoke(player, level, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                check((result == InteractionResult.SUCCESS) == expected
                        && level.getBlockState(pos).is(expected ? Blocks.PURPLE_CONCRETE : Blocks.MAGENTA_CONCRETE), "two-hand count matching");
                if (expected) check(player.getOffhandItem().isEmpty(), "two-hand counted ingredient consumption");
            }
            evidence.add("schema_two_hand_counted_interaction");
        } finally {
            manager.replaceRecipes(original);
            refresh();
            level.setBlock(pos, state, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
            player.setShiftKeyDown(sneaking);
        }
        return evidence;
    }

    private static void assertInteraction(ServerLevel level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            boolean sneaking, ItemStack main, ItemStack off, boolean expected) {
        level.setBlock(pos, Blocks.LIME_CONCRETE.defaultBlockState(), 3);
        player.setShiftKeyDown(sneaking);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        var result = BlockInteractingRecipe.invoke(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        check((result == InteractionResult.SUCCESS) == expected && level.getBlockState(pos).is(expected ? Blocks.YELLOW_CONCRETE : Blocks.LIME_CONCRETE),
                "native interaction sneaking=" + sneaking + ", main=" + main + ", off=" + off + ", result=" + result);
    }

    private static void assertSchemaInteraction(ServerLevel level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            boolean sneaking, ItemStack main, boolean expected) {
        level.setBlock(pos, Blocks.CYAN_CONCRETE.defaultBlockState(), 3);
        player.setShiftKeyDown(sneaking);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        var result = BlockInteractingRecipe.invoke(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        check((result == InteractionResult.SUCCESS) == expected && level.getBlockState(pos).is(expected ? Blocks.BLUE_CONCRETE : Blocks.CYAN_CONCRETE),
                "KubeJS interaction sneaking=" + sneaking + ", main=" + main + ", result=" + result);
    }

    private static void refresh() {
        RecipeTypes.BLOCK_INTERACTING.refreshCache();
        RecipeTypes.BLOCK_INTERACTING.updateEmptyState();
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("lychee")) event.register(LycheeEmptyHandGameTests.class);
        }
    }
}
