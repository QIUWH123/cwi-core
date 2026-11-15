package net.quiwh.cwi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.block.BlockBuilder;
import dev.latvian.mods.kubejs.generator.KubeAssetGenerator;
import dev.latvian.mods.kubejs.registry.RegistryObjectStorage;
import dev.latvian.mods.kubejs.script.data.GeneratedData;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Generated resources are checked as consumed JSON, without starting a client. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class CrystalAssetGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void crystalModelsUseVanillaTemplatesAndSixFacings(GameTestHelper helper) {
        var cluster = capture("cluster");
        var model = json(cluster, "models/block/crystal_contract_cluster.json");
        helper.assertTrue(model.get("parent").getAsString().equals("minecraft:block/cross"),
                "clusters use the vanilla cross geometry");
        helper.assertTrue(model.getAsJsonObject("textures").get("cross").getAsString()
                        .equals("minecraft:block/amethyst_cluster"), "cluster uses script texture");
        var variants = json(cluster, "blockstates/crystal_contract_cluster.json").getAsJsonObject("variants");
        helper.assertTrue(variants.size() == 6, "six facing variants cover both waterlogged states");
        for (String face : new String[]{"up", "down", "north", "south", "east", "west"}) {
            helper.assertTrue(variants.has("facing=" + face), "facing model exists: " + face);
        }
        var source = capture("source");
        helper.assertTrue(json(source, "models/block/crystal_contract_source.json")
                        .get("parent").getAsString().equals("minecraft:block/cube_all"),
                "budding defaults to vanilla full cube template");
        for (String stage : new String[]{"small", "medium", "large", "cluster"}) {
            var item = json(capture(stage), "models/item/crystal_contract_" + stage + ".json");
            String nativeItem = stage.equals("cluster") ? "amethyst_cluster" : stage + "_amethyst_bud";
            helper.assertTrue(item.get("parent").getAsString().equals("minecraft:item/" + nativeItem),
                    "item uses native amethyst sprite and display transforms: " + stage);
            helper.assertTrue(item.getAsJsonObject("textures").get("layer0").getAsString()
                            .equals("minecraft:block/amethyst_cluster"), "item keeps script texture");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void explicitModelsRemainOwnedByTheResourcePack(GameTestHelper helper) {
        for (String suffix : new String[]{"model", "source_model"}) {
            var assets = capture(suffix);
            helper.assertTrue(!assets.containsKey(ResourceLocation.parse(
                            "cwi:models/block/crystal_contract_" + suffix + ".json")),
                    "explicit model is not replaced with generated geometry");
            String expected = suffix.equals("model") ? "minecraft:block/amethyst_cluster" : "minecraft:block/amethyst_block";
            helper.assertTrue(json(assets, "models/item/crystal_contract_" + suffix + ".json")
                            .get("parent").getAsString().equals(expected), "item points to explicit block model");
            var variants = json(assets, "blockstates/crystal_contract_" + suffix + ".json").getAsJsonObject("variants");
            for (var entry : variants.entrySet()) {
                var value = entry.getValue();
                if (value.isJsonArray()) value = value.getAsJsonArray().get(0);
                helper.assertTrue(value.getAsJsonObject().get("model").getAsString().equals(expected),
                        "every block state uses the explicit model");
            }
        }
        helper.succeed();
    }

    private static Map<ResourceLocation, GeneratedData> capture(String suffix) {
        var builder = (BlockBuilder) RegistryObjectStorage.BLOCK.objects.get(
                ResourceLocation.parse("cwi:crystal_contract_" + suffix));
        if (builder == null) throw new AssertionError("crystal builder registered: " + suffix);
        var assets = new HashMap<ResourceLocation, GeneratedData>();
        builder.generateAssets(new KubeAssetGenerator() {
            @Override public RegistryAccessContainer getRegistries() { return RegistryAccessContainer.current; }
            @Override public void add(GeneratedData data) { assets.put(data.id(), data); }
            @Override public GeneratedData getGenerated(ResourceLocation id) { return assets.get(id); }
        });
        return assets;
    }

    private static JsonObject json(Map<ResourceLocation, GeneratedData> assets, String path) {
        var data = assets.get(ResourceLocation.parse("cwi:" + path));
        if (data == null) throw new AssertionError("generated resource exists: " + path);
        return JsonParser.parseString(new String(data.data().get(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
