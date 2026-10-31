package net.quiwh.cwi.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pack policy, independent of Vintage/KubeJS classes and active before either mod registers content. */
public final class VintageMaterialPolicy {
    private static final String MOD = "vintageimprovements";
    private static final Set<String> RAW_ITEMS = Set.of("vanadium_ingot", "vanadium_nugget", "vanadium_block",
            "sulfur", "sulfur_chunk", "sulfur_block", "copper_sulfate", "sulfuric_acid_bucket");
    private static final Set<String> OBSOLETE_COMPONENTS = Set.of("redstone_module", "incomplete_redstone_module");
    private static final Set<String> BLOCKS = Set.of("vanadium_block", "sulfur_block", "sulfuric_acid");
    private static final Set<String> FLUIDS = Set.of("sulfuric_acid", "flowing_sulfuric_acid", "sulfur_dioxide", "sulfur_trioxide");
    private static final ResourceLocation FLUID_TYPE = ResourceLocation.fromNamespaceAndPath("neoforge", "fluid_type");
    private static final ResourceLocation ROOT_ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(MOD, "root");

    private VintageMaterialPolicy() {}

    public static boolean blocks(ResourceLocation registry, ResourceLocation id) {
        if (id == null || !MOD.equals(id.getNamespace())) return false;
        String path = id.getPath();
        if (registry.equals(Registries.ITEM.location())) return RAW_ITEMS.contains(path) || OBSOLETE_COMPONENTS.contains(path) || shape(path);
        if (registry.equals(Registries.BLOCK.location())) return BLOCKS.contains(path);
        if (registry.equals(Registries.FLUID.location()) || registry.equals(FLUID_TYPE)) return FLUIDS.contains(path);
        return false;
    }

    private static boolean shape(String path) {
        return path.endsWith("_rod") || path.endsWith("_sheet") || path.endsWith("_wire") || path.endsWith("_spring");
    }

    private static boolean material(String text) {
        var id = ResourceLocation.tryParse(text);
        return id != null && MOD.equals(id.getNamespace()) && (RAW_ITEMS.contains(id.getPath()) || OBSOLETE_COMPONENTS.contains(id.getPath())
                || BLOCKS.contains(id.getPath()) || FLUIDS.contains(id.getPath()) || shape(id.getPath()));
    }

    private static boolean referencesMaterial(JsonElement value) {
        if (value.isJsonPrimitive()) return value.getAsJsonPrimitive().isString() && material(value.getAsString());
        if (value.isJsonArray()) {
            for (var entry : value.getAsJsonArray()) if (referencesMaterial(entry)) return true;
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) if (referencesMaterial(entry.getValue())) return true;
        }
        return false;
    }

    /** Filter parsed definitions before codecs resolve removed registry values. Every reload starts afresh. */
    public static void filterData(String directory, Map<ResourceLocation, JsonElement> definitions) {
        if (directory.equals("recipe")) {
            definitions.entrySet().removeIf(entry -> {
                var json = entry.getValue();
                if (!referencesMaterial(json)) return false;
                // Preserve ordinary ore processing when Vintage only adds a removed byproduct.
                if (json.isJsonObject() && json.getAsJsonObject().has("results") && json.getAsJsonObject().get("results").isJsonArray()) {
                    var outputs = json.getAsJsonObject().getAsJsonArray("results");
                    for (int i = outputs.size() - 1; i >= 0; i--) if (referencesMaterial(outputs.get(i))) outputs.remove(i);
                    if (outputs.isEmpty()) return true;
                }
                return referencesMaterial(json);
            });
        } else if (directory.equals("loot_table")) {
            definitions.entrySet().removeIf(entry -> entry.getKey().getNamespace().equals(MOD) && referencesMaterial(entry.getValue()));
        } else if (directory.equals("advancement")) {
            var removed = new HashSet<ResourceLocation>();
            definitions.entrySet().removeIf(entry -> {
                if (!entry.getKey().getNamespace().equals(MOD) || !entry.getValue().isJsonObject()) return false;
                JsonObject json = entry.getValue().getAsJsonObject();
                if (json.has("criteria") && referencesMaterial(json.get("criteria"))) { removed.add(entry.getKey()); return true; }
                if (json.has("display") && json.get("display").isJsonObject()) {
                    var display = json.getAsJsonObject("display");
                    if (display.has("icon") && referencesMaterial(display.get("icon"))) {
                        var icon = new JsonObject(); icon.addProperty("id", MOD + ":centrifuge"); display.add("icon", icon);
                    }
                }
                return false;
            });
            for (var entry : definitions.entrySet()) {
                if (!entry.getKey().getNamespace().equals(MOD) || !entry.getValue().isJsonObject()) continue;
                var json = entry.getValue().getAsJsonObject();
                if (json.has("parent") && json.get("parent").isJsonPrimitive()
                        && removed.contains(ResourceLocation.tryParse(json.get("parent").getAsString())))
                    json.addProperty("parent", ROOT_ADVANCEMENT.toString());
            }
        }
    }
}
