package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import dev.latvian.mods.kubejs.codec.KubeJSCodecs;
import dev.latvian.mods.kubejs.plugin.builtin.BuiltinKubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.RecipeTypeRegistryContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ItemMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaData;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.recipe.schema.postprocessing.RecipePostProcessorType;
import dev.latvian.mods.kubejs.util.ErrorStack;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.kubejs.util.RegistryOpsContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.compat.CwiKubeJSPlugin;
import net.quiwh.cwi.compat.kubejs.CwiProcessingOutputComponent;

/** Run with KubeJS, both with and without its optional Create addon. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class CwiKubeJSRecipeTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void standaloneFanSchemas(GameTestHelper helper) throws Exception {
        if (ModList.get().isLoaded("kubejs")) verify(helper.getLevel().registryAccess());
        helper.succeed();
    }

    public static void verify(RegistryAccess registryAccess) throws Exception {
        var types = new ArrayList<RecipeComponentType<?>>();
        new CwiKubeJSPlugin().registerRecipeComponents(types::add);
        require(types.contains(CwiProcessingOutputComponent.TYPE), "CWI registers its own output component");
        var component = CwiProcessingOutputComponent.TYPE.instance();
        var registries = new RegistryAccessContainer(registryAccess.freeze());
        var cx = new ContextFactory().enter();
        RecipeScriptContext context = new RecipeScriptContext() {
            @Override public Context cx() { return cx; }
            @Override public RegistryAccessContainer registries() { return registries; }
            @Override public RegistryOpsContainer ops() { return registries; }
            @Override public KubeRecipe recipe() { return null; }
            @Override public ErrorStack errors() { return ErrorStack.NONE; }
        };

        var plain = component.wrap(context, new ItemStack(Items.GOLD_NUGGET, 2));
        require(plain.getStack().getCount() == 2 && plain.getChance() == 1F, "plain stacks retain counts");
        require(component.wrap(context, plain) == plain, "native Create outputs remain accepted");
        var output = component.wrap(context, Map.of("id", "minecraft:gold_nugget", "count", 2, "chance", 0.32,
                "components", Map.of("minecraft:custom_data", Map.of("cwi_probe", 7))));
        require(output.getStack().is(Items.GOLD_NUGGET) && output.getStack().getCount() == 2,
                "map outputs retain item and count");
        require(output.getChance() == 0.32F, "map outputs retain chance");
        require(output.getStack().get(DataComponents.CUSTOM_DATA).copyTag().getInt("cwi_probe") == 7,
                "map outputs retain item components");
        var legacy = component.wrap(context, Map.of("item", "minecraft:gold_nugget", "count", 3, "chance", 0.5));
        require(legacy.getStack().getCount() == 3 && legacy.getChance() == 0.5F, "legacy item keys remain accepted");
        var nested = component.wrap(context, Map.of("stack", new ItemStack(Items.GOLD_NUGGET, 4), "chance", 0.25));
        require(nested.getStack().getCount() == 4 && nested.getChance() == 0.25F, "stack and chance pairs remain accepted");
        var js = cx.evaluateString(cx.initStandardObjects(),
                "({id: 'minecraft:gold_nugget', count: 3, chance: 0.4})", "cwi-output-contract", 1, null);
        var scripted = component.wrap(context, js);
        require(scripted.getStack().getCount() == 3 && scripted.getChance() == 0.4F,
                "actual JavaScript objects retain output count and chance");
        var match = new ReplacementMatchInfo(new ItemMatch() {
            @Override public boolean matches(RecipeMatchContext unused, ItemStack stack, boolean exact) {
                return stack.is(Items.GOLD_NUGGET);
            }
            @Override public boolean matches(RecipeMatchContext unused, Ingredient ingredient, boolean exact) { return false; }
        }, false, Optional.empty());
        var replaced = component.replace(context, output, match, new ItemStack(Items.IRON_INGOT));
        require(replaced.getStack().is(Items.IRON_INGOT) && replaced.getStack().getCount() == 2
                && replaced.getChance() == 0.32F, "plain output replacement preserves original count and chance");
        var explicit = component.replace(context, output, match, Map.of("id", "minecraft:iron_ingot", "count", 4, "chance", 0.75));
        require(explicit.getStack().getCount() == 4 && explicit.getChance() == 0.75F,
                "explicit chance output replacement uses supplied count and chance");
        var encoded = component.codec().encodeStart(registries.json(), output).getOrThrow();
        var decoded = component.codec().parse(registries.json(), encoded).getOrThrow();
        require(ItemStack.isSameItemSameComponents(output.getStack(), decoded.getStack())
                && decoded.getStack().getCount() == 2 && decoded.getChance() == 0.32F, "native output codec round trip");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
            ProcessingOutput.STREAM_CODEC.encode(buffer, output);
            var synced = ProcessingOutput.STREAM_CODEC.decode(buffer);
            require(ItemStack.isSameItemSameComponents(output.getStack(), synced.getStack())
                    && synced.getStack().getCount() == 2 && synced.getChance() == 0.32F, "native output network round trip");
        } finally {
            buffer.release();
        }

        var componentTypes = new HashMap<ResourceLocation, RecipeComponentType<?>>();
        new BuiltinKubeJSPlugin().registerRecipeComponents(type -> componentTypes.put(type.id(), type));
        new CwiKubeJSPlugin().registerRecipeComponents(type -> componentTypes.put(type.id(), type));
        var storage = new RecipeSchemaStorage(null);
        var registryContext = new RecipeTypeRegistryContext(registries, storage);
        Codec<RecipeComponent<?>> unitCodec = KubeJSCodecs.KUBEJS_ID.comapFlatMap(id -> {
            var type = componentTypes.get(id);
            return type == null ? DataResult.error(() -> "Unknown component " + id) : DataResult.success(type.instance());
        }, value -> value.type().id());
        Codec<RecipeComponent<?>> objectCodec = KubeJSCodecs.KUBEJS_ID.dispatch("type", value -> value.type().id(),
                id -> componentTypes.get(id).mapCodec(registryContext));
        storage.recipeComponentCodec = Codec.either(unitCodec, objectCodec).xmap(
                value -> value.map(left -> left, right -> right), Either::left);
        var postProcessors = new HashMap<ResourceLocation, RecipePostProcessorType<?>>();
        new BuiltinKubeJSPlugin().registerRecipePostProcessors(type -> postProcessors.put(type.id(), type));
        storage.recipePostProcessorCodec = KubeJSCodecs.KUBEJS_ID.dispatch("type", value -> value.type().id(),
                id -> postProcessors.get(id).mapCodec().apply(registryContext));
        for (String path : new String[]{"corroding", "sterilzing"}) {
            try (var stream = CwiKubeJSPlugin.class.getResourceAsStream("/data/cwi/kubejs/recipe_schema/" + path + ".json")) {
                var schema = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
                require(!schema.has("parent"), "fan schema has no optional external parent");
                var keys = schema.getAsJsonArray("keys");
                require(keys.get(0).getAsJsonObject().get("name").getAsString().equals("results")
                        && keys.get(1).getAsJsonObject().get("name").getAsString().equals("ingredients"),
                        "fan DSL keeps output then input arguments");
                require(keys.get(2).getAsJsonObject().get("name").getAsString().equals("processing_time")
                        && keys.get(2).getAsJsonObject().get("optional").getAsInt() == 100,
                        "fan DSL keeps optional processing time");
                var decodedSchema = RecipeSchemaData.CODEC.apply(registryContext).parse(registries.json(), schema).getOrThrow();
                MachineKubeJSRecipeTests.verifyNumericDuration(decodedSchema.keys().orElseThrow().get(2).type(), context, path);
                require(decodedSchema.keys().orElseThrow().size() == 3,
                        "native KubeJS decodes self-contained schema with only builtin and CWI components");
            }
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
