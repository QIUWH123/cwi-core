package net.quiwh.cwi;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwi.registry.RegistrationBlocker;

import java.util.LinkedHashSet;
import java.util.Set;

/** Runs in a separate JVM whose classpath deliberately excludes KubeJS and Rhino. */
public final class RegistrationRulesWithoutKubeJSTest {
    public static void main(String[] args) throws Exception {
        try {
            Class.forName("dev.latvian.mods.kubejs.KubeJS");
            throw new AssertionError("This fixture must run without KubeJS on its classpath");
        } catch (ClassNotFoundException expected) {}
        var blocked = ResourceLocation.parse("fixture:blocked");
        var kept = ResourceLocation.parse("fixture:kept");
        vintagePolicy();
        require(!RegistrationBlocker.blocks(Registries.ITEM.location(), blocked), "no rules is inert");
        var source = new LinkedHashSet<>(Set.of(blocked));
        RegistrationBlocker.configure(source, Set.of());
        source.clear();
        source.add(kept);
        require(RegistrationBlocker.blocks(Registries.BLOCK.location(), blocked), "rules own an immutable snapshot");
        require(!RegistrationBlocker.blocks(Registries.BLOCK.location(), kept), "later collection mutations cannot add rules");
        require(!RegistrationBlocker.blocks(Registries.ITEM.location(), blocked), "item registry is independent");
        require(!RegistrationBlocker.blocks(Registries.SOUND_EVENT.location(), blocked), "other registries stay inert");
        require(!RegistrationBlocker.blocks(Registries.BLOCK.location(), null), "null lookup preserves native resolution");
        try {
            RegistrationBlocker.configure(Set.of(), Set.of(kept));
            throw new AssertionError("registration rules changed after configuration");
        } catch (IllegalStateException expected) {}
        require(RegistrationBlocker.blocks(Registries.BLOCK.location(), blocked)
                && !RegistrationBlocker.blocks(Registries.ITEM.location(), kept), "failed reconfiguration preserves rules");
        System.out.println("Registration rules: optional-KubeJS loading and immutable lifecycle passed");
    }

    private static void vintagePolicy() {
        var spring = ResourceLocation.parse("vintageimprovements:small_iron_spring");
        require(RegistrationBlocker.blocks(Registries.ITEM.location(), spring), "permanent material rules run without KubeJS configuration");
        require(!RegistrationBlocker.blocks(Registries.BLOCK.location(), spring)
                && !RegistrationBlocker.blocks(Registries.SOUND_EVENT.location(), spring), "shape rule applies only to items");
        require(!RegistrationBlocker.blocks(Registries.ITEM.location(), ResourceLocation.parse("create:iron_sheet")), "namespace boundary");
        require(!RegistrationBlocker.blocks(Registries.ITEM.location(), ResourceLocation.parse("vintageimprovements:grinder_belt")), "functional components retained");
        for (String path : Set.of("redstone_module", "incomplete_redstone_module")) {
            var module = ResourceLocation.fromNamespaceAndPath("vintageimprovements", path);
            require(RegistrationBlocker.blocks(Registries.ITEM.location(), module), "obsolete module removed without KubeJS");
            require(!RegistrationBlocker.blocks(Registries.BLOCK.location(), module), "module policy is item-only");
        }

        var recipes = new java.util.HashMap<ResourceLocation, com.google.gson.JsonElement>();
        var ore = ResourceLocation.parse("create:crushing/test_ore");
        var doomed = ResourceLocation.parse("vintageimprovements:craft/vanadium");
        var input = ResourceLocation.parse("other:using_sulfur");
        var json = com.google.gson.JsonParser.parseString("""
                {"type":"create:crushing","ingredients":[{"item":"minecraft:iron_ore"}],"results":[
                  {"id":"minecraft:raw_iron","count":3,"chance":0.75},
                  {"id":"vintageimprovements:vanadium_nugget","chance":0.1}]}
                """);
        recipes.put(ore, json);
        recipes.put(doomed, com.google.gson.JsonParser.parseString("""
                {"type":"minecraft:crafting_shapeless","ingredients":[{"tag":"c:ingots/vanadium"}],
                 "result":{"id":"vintageimprovements:vanadium_nugget","count":9}}
                """));
        recipes.put(input, com.google.gson.JsonParser.parseString("""
                {"type":"create:mixing","ingredients":[{"item":"vintageimprovements:sulfur"}],
                 "results":[{"id":"minecraft:diamond"}]}
                """));
        net.quiwh.cwi.compat.VintageMaterialPolicy.filterData("recipe", recipes);
        require(recipes.size() == 1 && recipes.containsKey(ore), "removed inputs and sole outputs drop their recipes");
        var outputs = json.getAsJsonObject().getAsJsonArray("results");
        require(outputs.size() == 1 && outputs.get(0).getAsJsonObject().get("count").getAsInt() == 3
                && outputs.get(0).getAsJsonObject().get("chance").getAsDouble() == .75, "ore output/count/chance survive removed byproducts");
        var snapshot = recipes.toString();
        net.quiwh.cwi.compat.VintageMaterialPolicy.filterData("recipe", recipes);
        require(snapshot.equals(recipes.toString()), "repeated filters are idempotent");

        var advancements = new java.util.HashMap<ResourceLocation, com.google.gson.JsonElement>();
        var root = ResourceLocation.parse("vintageimprovements:root");
        var material = ResourceLocation.parse("vintageimprovements:iron_spring");
        var machine = ResourceLocation.parse("vintageimprovements:centrifuge");
        advancements.put(root, com.google.gson.JsonParser.parseString("""
                {"display":{"icon":{"id":"vintageimprovements:brass_spring"}},"criteria":{"root":{}}}
                """));
        advancements.put(material, com.google.gson.JsonParser.parseString("""
                {"criteria":{"spring":{"items":["vintageimprovements:iron_spring"]}}}
                """));
        advancements.put(machine, com.google.gson.JsonParser.parseString("""
                {"parent":"vintageimprovements:iron_spring","criteria":{"machine":{}}}
                """));
        net.quiwh.cwi.compat.VintageMaterialPolicy.filterData("advancement", advancements);
        require(!advancements.containsKey(material), "material advancement removed");
        require(advancements.get(machine).getAsJsonObject().get("parent").getAsString().equals(root.toString()), "machine advancement reparented");
        require(advancements.get(root).getAsJsonObject().getAsJsonObject("display").getAsJsonObject("icon").get("id").getAsString()
                .equals("vintageimprovements:centrifuge"), "root uses retained icon");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
