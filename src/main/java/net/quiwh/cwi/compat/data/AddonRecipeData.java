package net.quiwh.cwi.compat.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwi.compat.VintageMaterialPolicy;

import java.util.Map;
import java.util.Set;

/** Narrow, idempotent repairs for verified upstream data; authored native-format overrides take precedence. */
public final class AddonRecipeData {
    private static final Set<String> ORE_OVERRIDES = Set.of("crushing/asurine", "crushing/asurine_recycling",
            "crushing/crimsite", "crushing/crimsite_recycling");

    public static void filter(String directory, Map<ResourceLocation, JsonElement> definitions) {
        VintageMaterialPolicy.filterData(directory, definitions);
        if (!directory.equals("recipe")) return;
        definitions.forEach((id, value) -> {
            if (!id.getNamespace().equals("create") || !ORE_OVERRIDES.contains(id.getPath()) || !value.isJsonObject()) return;
            var json = value.getAsJsonObject();
            if (!isType(json, "create:crushing")) return;
            rename(json, "processingTime", "processing_time");
            if (json.has("results") && json.get("results").isJsonArray())
                for (var result : json.getAsJsonArray("results")) if (result.isJsonObject()) rename(result.getAsJsonObject(), "item", "id");
        });
    }

    private static boolean isType(JsonObject json, String type) {
        return json.has("type") && json.get("type").isJsonPrimitive() && json.get("type").getAsString().equals(type);
    }
    private static void rename(JsonObject json, String legacy, String nativeKey) {
        if (!json.has(legacy)) return;
        var value = json.remove(legacy);
        if (!json.has(nativeKey)) json.add(nativeKey, value);
    }
    private AddonRecipeData() {}
}
