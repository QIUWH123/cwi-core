package net.quiwh.cwi;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/** Native optional-mod registry/freeze/data/creative compatibility, never shipped in Core. */
@PrefixGameTestTemplate(false)
public final class VintageMaterialPreventionGameTests {
    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("vintageimprovements")) event.register(VintageMaterialPreventionGameTests.class);
        }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("vintageimprovements", path); }
    private static List<String> materialItems() {
        try (var input = VintageMaterialPreventionGameTests.class.getResourceAsStream("/vintage-material-items.txt")) {
            return Arrays.stream(new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")).filter(line -> !line.isBlank()).toList();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    private static void absent(GameTestHelper helper, Registry<?> registry, String path) {
        var id = id(path);
        helper.assertTrue(!registry.containsKey(id) && !registry.keySet().contains(id) && registry.getHolder(id).isEmpty(),
                "material must be absent from registry keys and holders: " + registry.key().location() + "/" + id);
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void allMaterialItemsAndStorageBlocksAreAbsent(GameTestHelper helper) {
        var expected = materialItems();
        helper.assertTrue(expected.size() == 224, "inventory derived from installed Vintage registrations");
        for (String item : expected) absent(helper, BuiltInRegistries.ITEM, item);
        for (String block : List.of("vanadium_block", "sulfur_block", "sulfuric_acid")) absent(helper, BuiltInRegistries.BLOCK, block);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void sulfurProcessFluidsAndTypesAreAbsent(GameTestHelper helper) {
        for (String fluid : List.of("sulfuric_acid", "flowing_sulfuric_acid", "sulfur_dioxide", "sulfur_trioxide"))
            absent(helper, BuiltInRegistries.FLUID, fluid);
        var types = net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES;
        for (String type : List.of("sulfuric_acid", "sulfur_dioxide", "sulfur_trioxide")) absent(helper, types, type);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void machinesAndMachineComponentsRemainRegistered(GameTestHelper helper) {
        for (String block : List.of("belt_grinder", "spring_coiling_machine", "vacuum_chamber", "vibrating_table", "centrifuge", "curving_press", "helve_hammer", "lathe", "laser"))
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id(block)), "machine remains available: " + block);
        for (String item : List.of("grinder_belt", "spring_coiling_machine_wheel", "laser_item", "recipe_card", "incomplete_recipe_card", "convex_curving_head", "concave_curving_head", "w_shaped_curving_head", "v_shaped_curving_head", "helve_hammer_slot_cover"))
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id(item)), "functional component remains: " + item);
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("minecraft:iron_ingot"))
                && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("create:iron_sheet")), "other namespaces preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void creativeTabCannotExposeRemovedMaterialObjects(GameTestHelper helper) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id("vintage_improvement_tab"));
        var cfg = com.negodya1.vintageimprovements.infrastructure.config.VintageConfig.common();
        var options = List.of(cfg.hideRods, cfg.hideSheets, cfg.hideWires, cfg.hideSprings, cfg.hideSmallSprings,
                cfg.forceCompatItemsIntoCreativeTab, cfg.legacyMaterialsIntoCreativeTab);
        var old = options.stream().map(option -> option.get()).toList();
        try {
            for (int i = 0; i < options.size(); i++) options.get(i).set(i >= 5);
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
            var items = new java.util.ArrayList<>(tab.getDisplayItems()); items.addAll(tab.getSearchTabDisplayItems());
            helper.assertTrue(!items.isEmpty(), "functional creative items preserved");
            for (var stack : items) helper.assertTrue(BuiltInRegistries.ITEM.getResourceKey(stack.getItem()).isPresent(),
                    "creative tab and search may only contain registered items");
        } finally { for (int i = 0; i < options.size(); i++) options.get(i).set(old.get(i)); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void removedMaterialDataDoesNotReachNativeDecoders(GameTestHelper helper) {
        var resources = helper.getLevel().getServer().getResourceManager();
        for (String directory : List.of("recipe", "loot_table")) {
            var definitions = new HashMap<ResourceLocation, JsonElement>();
            SimpleJsonResourceReloadListener.scanDirectory(resources, directory, new Gson(), definitions);
            helper.assertTrue(!definitions.isEmpty(), "native definitions must actually be scanned");
            if (directory.equals("recipe")) helper.assertTrue(definitions.containsKey(id("vibrating/snow")), "ordinary machine recipe retained");
            for (var entry : definitions.entrySet()) {
                if (!entry.getKey().getNamespace().equals("vintageimprovements")) continue;
                for (String item : materialItems()) helper.assertTrue(!entry.getValue().toString().contains("\"vintageimprovements:" + item + "\""),
                        directory + " retains an unavailable material: " + entry.getKey());
            }
        }
        helper.assertTrue(!helper.getLevel().registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).containsKey(id("vanadium")),
                "vanadium trim material is removed before its missing ingredient is decoded");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void nativeTagsAndAdvancementsKeepMachinesWithoutMaterials(GameTestHelper helper) {
        var resources = helper.getLevel().getServer().getResourceManager();
        for (String directory : List.of("tags/item", "tags/block", "tags/fluid")) {
            var tags = new net.minecraft.tags.TagLoader<Object>(key -> java.util.Optional.empty(), directory).load(resources);
            helper.assertTrue(!tags.isEmpty(), "native tag resources loaded: " + directory);
            var registry = switch (directory) {
                case "tags/item" -> Registries.ITEM.location();
                case "tags/block" -> Registries.BLOCK.location();
                default -> Registries.FLUID.location();
            };
            helper.assertTrue(tags.values().stream().flatMap(List::stream).noneMatch(entry -> !entry.entry().isTag()
                    && net.quiwh.cwi.compat.VintageMaterialPolicy.blocks(registry, entry.entry().getId())), "removed direct tag entries: " + directory);
            helper.assertTrue(tags.values().stream().flatMap(List::stream).anyMatch(entry -> entry.entry().isTag()), "symbolic tag references retained");
        }
        var advancements = new HashMap<ResourceLocation, JsonElement>();
        SimpleJsonResourceReloadListener.scanDirectory(resources, "advancement", new Gson(), advancements);
        helper.assertTrue(advancements.containsKey(id("root")) && advancements.containsKey(id("centrifuge")), "machine advancements retained");
        helper.assertTrue(!advancements.containsKey(id("iron_spring")) && !advancements.containsKey(id("vanadium_block")), "material advancements removed");
        helper.assertTrue(advancements.get(id("root")).getAsJsonObject().getAsJsonObject("display").getAsJsonObject("icon")
                .get("id").getAsString().equals("vintageimprovements:centrifuge"), "retained root icon");
        for (var entry : advancements.entrySet()) {
            if (!entry.getKey().getNamespace().equals("vintageimprovements")) continue;
            var json = entry.getValue().getAsJsonObject();
            if (json.has("parent")) helper.assertTrue(advancements.containsKey(ResourceLocation.parse(json.get("parent").getAsString())),
                    "surviving advancement parent exists: " + entry.getKey());
        }
        helper.succeed();
    }
}
