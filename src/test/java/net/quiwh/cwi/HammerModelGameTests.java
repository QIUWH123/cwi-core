package net.quiwh.cwi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.generator.KubeAssetGenerator;
import dev.latvian.mods.kubejs.script.data.GeneratedData;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.kubejs.registry.RegistryObjectStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.kubejs.HammerBuilder;

import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

/** Checks the generated assets consumers actually load, without a graphics context. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class HammerModelGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void exactPackModelMigrationRegistersEveryMaterial(GameTestHelper helper) {
        for (String material : new String[]{"bronze", "industrial_iron", "brass", "steel", "stainless_steel", "iron",
                "rusted_iron", "stone", "golden", "zinc", "copper", "lead", "netherite"}) {
            String id = "minecraft:" + material + "_hammer";
            var builder = RegistryObjectStorage.ITEM.objects.get(ResourceLocation.parse(id));
            require(builder instanceof HammerBuilder, "exact migrated pack script registers " + id);
            var generated = capture((HammerBuilder) builder);
            require(generated.size() == 4, "each of thirteen real pack materials generates its normal and three charge models");
            require(model(generated, "minecraft:models/item/" + material + "_hammer.json")
                    .getAsJsonObject("textures").get("layer0").getAsString().equals("kubejs:item/tools/" + material + "_hammer"),
                    "original pack material texture retained for " + id);
        }
        var pipe = (HammerBuilder) RegistryObjectStorage.ITEM.objects.get(ResourceLocation.parse("kubejs:steel_pipe"));
        require(pipe != null && capture(pipe).size() == 1 && pipe.behavior.releaseUsing != null,
                "exact migrated pipe registration retains its manual model and original release callback");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void originalMaterialModelParity(GameTestHelper helper) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                HammerModelGameTests.class.getResourceAsStream("/hammer-original-models.json")), StandardCharsets.UTF_8)) {
            var references = JsonParser.parseReader(reader).getAsJsonArray();
            require(references.size() == 34, "all authored original ordinary charge models are present in the fixture");
            for (var reference : references) {
                var entry = reference.getAsJsonObject();
                String item = entry.get("item").getAsString();
                int stage = entry.get("stage").getAsInt();
                var builder = new HammerBuilder(ResourceLocation.parse("minecraft:" + item));
                builder.texture("kubejs:item/tools/" + item);
                if (item.equals("stone_hammer")) builder.chargedTexture(1, "kubejs:item/tools/charged_stone_hammer");
                var actual = model(capture(builder), "minecraft:models/item/" + item + "/charged_" + stage + ".json");
                var expected = entry.getAsJsonObject("expected").deepCopy();
                require(actual.equals(expected), "generated model matches authored original: " + item + " stage " + stage);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void startupFluentModelsUseNativeGenerator(GameTestHelper helper) {
        var registered = RegistryObjectStorage.ITEM.objects.get(ResourceLocation.parse("cwi:model_contract_hammer"));
        require(registered instanceof HammerBuilder, "real startup script registers fluent model settings");
        var textures = model(capture((HammerBuilder) registered),
                "cwi:models/item/model_contract_hammer/charged_1.json").getAsJsonObject("textures");
        require(textures.get("layer0").getAsString().equals("kubejs:item/tools/iron_hammer") && !textures.has("layer1"),
                "Rhino chargedTexture call reaches generation after inherited fluent methods");
        var manual = RegistryObjectStorage.ITEM.objects.get(ResourceLocation.parse("cwi:manual_model_contract_hammer"));
        require(manual instanceof HammerBuilder && capture((HammerBuilder) manual).size() == 1,
                "Rhino chargeModels(false) preserves manual model ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void materialTextureGetsAllChargeModels(GameTestHelper helper) {
        var builder = new HammerBuilder(ResourceLocation.parse("example:tools/copper_hammer"));
        builder.texture("pack:item/copper_hammer");
        builder.maxStage(2);
        var models = capture(builder);
        var base = model(models, "example:models/item/tools/copper_hammer.json");
        require(base.getAsJsonObject("textures").get("layer0").getAsString().equals("pack:item/copper_hammer"),
                "normal model retains the selected material texture");
        require(base.has("overrides") && base.getAsJsonArray("overrides").size() == 3,
                "ordinary hammer generates all three charge overrides without handwritten JSON");
        for (int stage = 1; stage <= 3; stage++) {
            var override = base.getAsJsonArray("overrides").get(stage - 1).getAsJsonObject();
            require(override.getAsJsonObject("predicate").get("minecraft:custom_model_data").getAsInt() == stage,
                    "charge predicates are ordered by stage");
            String path = "example:item/tools/copper_hammer/charged_" + stage;
            require(override.get("model").getAsString().equals(path), "overrides resolve in the owning namespace");
            var charged = model(models, "example:models/item/tools/copper_hammer/charged_" + stage + ".json");
            require(charged.get("parent").getAsString().equals("minecraft:item/handheld"), "handheld transform survives");
            var textures = charged.getAsJsonObject("textures");
            require(textures.get("layer0").getAsString().equals("pack:item/copper_hammer"), "charged material retained");
            require(textures.get("layer1").getAsString().equals("kubejs:item/tools/charged_overlay_" + stage),
                    "each charged model uses the matching original animated overlay");
            require(!charged.has("overrides"), "charge submodels never recursively override themselves");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void textureLayersAndNamespacesStayIndependent(GameTestHelper helper) {
        var first = new HammerBuilder(ResourceLocation.parse("first:hammer"));
        first.texture("first:item/base");
        first.texture(new String[]{"layer0"}, "first:item/replaced");
        first.texture(new String[]{"layer2"}, "first:item/detail");
        first.parentModel(ResourceLocation.parse("pack:item/custom_handheld"));
        var second = new HammerBuilder(ResourceLocation.parse("second:hammer"));
        var a = capture(first);
        var b = capture(second);
        var charged = model(a, "first:models/item/hammer/charged_2.json");
        require(charged.get("parent").getAsString().equals("pack:item/custom_handheld"), "custom handheld parent retained");
        require(charged.getAsJsonObject("textures").get("layer0").getAsString().equals("first:item/replaced"),
                "explicit layer0 takes precedence over baseTexture");
        require(charged.getAsJsonObject("textures").get("layer2").getAsString().equals("first:item/detail"),
                "extra material layers survive generation");
        require(model(b, "second:models/item/hammer/charged_1.json").getAsJsonObject("textures")
                .get("layer0").getAsString().equals("second:item/hammer"), "default item texture uses its own namespace");
        require(!a.keySet().stream().anyMatch(id -> id.getNamespace().equals("second")), "same paths cannot mix namespaces");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void stoneStyleUsesItsAuthoredChargedTexture(GameTestHelper helper) throws Exception {
        var builder = new HammerBuilder(ResourceLocation.parse("pack:stone_hammer"));
        builder.texture("pack:item/stone_hammer");
        HammerBuilder.class.getMethod("chargedTexture", int.class, String.class)
                .invoke(builder, 1, "pack:item/charged_stone_hammer");
        var models = capture(builder);
        var charged = model(models, "pack:models/item/stone_hammer/charged_1.json").getAsJsonObject("textures");
        require(charged.get("layer0").getAsString().equals("pack:item/charged_stone_hammer"), "dedicated stone charge art retained");
        require(!charged.has("layer1"), "dedicated stone charge texture does not receive a second overlay");
        require(model(models, "pack:models/item/stone_hammer/charged_2.json").getAsJsonObject("textures")
                .get("layer1").getAsString().equals("kubejs:item/tools/charged_overlay_2"), "other stages keep ordinary generation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void customModelsOwnTheirChargeOverrides(GameTestHelper helper) throws Exception {
        var builder = new HammerBuilder(ResourceLocation.parse("pack:custom_pipe"));
        builder.modelGenerator(model -> {
            model.parent(ResourceLocation.parse("pack:item/pipe_geometry"));
            model.override(ResourceLocation.parse("pack:item/custom_pipe_charged"),
                    override -> override.predicate(ResourceLocation.parse("minecraft:custom_model_data"), 1));
        });
        var models = capture(builder);
        require(models.size() == 1, "explicit model generator does not generate ordinary hammer variants");
        var base = model(models, "pack:models/item/custom_pipe.json");
        require(base.get("parent").getAsString().equals("pack:item/pipe_geometry"), "custom 3D parent retained");
        require(base.getAsJsonArray("overrides").size() == 1, "custom charge overrides remain untouched");

        var manual = new HammerBuilder(ResourceLocation.parse("pack:manual_pipe"));
        HammerBuilder.class.getMethod("chargeModels", boolean.class).invoke(manual, false);
        manual.parentModel(ResourceLocation.parse("pack:item/manual_geometry"));
        var disabled = capture(manual);
        require(disabled.size() == 1 && !model(disabled, "pack:models/item/manual_pipe.json").has("overrides"),
                "manual model opt-out leaves charge models to the existing assets");
        helper.succeed();
    }

    private static Map<ResourceLocation, GeneratedData> capture(HammerBuilder builder) {
        var generated = new HashMap<ResourceLocation, GeneratedData>();
        builder.generateAssets(new KubeAssetGenerator() {
            @Override public RegistryAccessContainer getRegistries() { return RegistryAccessContainer.current; }
            @Override public void add(GeneratedData data) { generated.put(data.id(), data); }
            @Override public GeneratedData getGenerated(ResourceLocation id) { return generated.get(id); }
        });
        return generated;
    }

    private static JsonObject model(Map<ResourceLocation, GeneratedData> models, String id) {
        var data = models.get(ResourceLocation.parse(id));
        require(data != null, "generated model exists: " + id);
        return JsonParser.parseString(new String(data.data().get(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
