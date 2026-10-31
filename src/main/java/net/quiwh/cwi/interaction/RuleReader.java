package net.quiwh.cwi.interaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict numeric and field validation shared by the data-only rule families. */
final class RuleReader<T> {
    private final JsonObject json;
    private final DynamicOps<T> ops;
    private final Set<String> consumed = new HashSet<>(Set.of("type", "neoforge:conditions", "_kubejs_changed_marker"));

    RuleReader(JsonObject json, DynamicOps<T> ops) { this.json = json; this.ops = ops; }
    boolean has(String field) { return json.has(field); }
    JsonElement required(String field) {
        consumed.add(field);
        if (!json.has(field)) throw new IllegalArgumentException("Missing '" + field + "'");
        return json.get(field);
    }
    <V> V value(String field, Codec<V> codec) {
        return codec.parse(ops, JsonOps.INSTANCE.convertTo(ops, required(field))).getOrThrow(
                message -> new IllegalArgumentException(field + ": " + message));
    }
    String string(String field) {
        var value = required(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(field + " must be a string");
        return value.getAsString();
    }
    String string(String field, String fallback) { return has(field) ? string(field) : fallback; }
    ResourceLocation id(String field) { return ResourceLocation.parse(string(field)); }
    <V> V registered(String field, Registry<V> registry) {
        var id = id(field);
        return registry.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Unknown " + field + ": " + id));
    }
    double number(String field, double fallback, double min, double max) {
        if (!has(field)) return fallback;
        var element = required(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(field + " must be a number");
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(field + " must be finite and in [" + min + "," + max + "]");
        return value;
    }
    int integer(String field, int fallback, int min, int max) {
        double value = number(field, fallback, min, max);
        if (value != (int) value) throw new IllegalArgumentException(field + " must be an integer");
        return (int) value;
    }
    boolean bool(String field, boolean fallback) {
        if (!has(field)) return fallback;
        var value = required(field);
        // JSON has booleans; NBT (also used by the native network codec) stores
        // them as bytes. Let the original ops distinguish valid representations.
        boolean result = Codec.BOOL.parse(ops, JsonOps.INSTANCE.convertTo(ops, value))
                .getOrThrow(message -> new IllegalArgumentException(field + " must be a boolean: " + message));
        json.addProperty(field, result);
        return result;
    }
    List<JsonElement> list(String field, int min, int max) {
        var element = required(field);
        if (!element.isJsonArray() || element.getAsJsonArray().size() < min || element.getAsJsonArray().size() > max)
            throw new IllegalArgumentException(field + " must contain " + min + " to " + max + " entries");
        return List.copyOf(element.getAsJsonArray().asList());
    }
    RuleReader<T> child(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("Expected a rule object");
        return new RuleReader<>(element.getAsJsonObject(), ops);
    }
    void finish() {
        for (String field : json.keySet()) if (!consumed.contains(field))
            throw new IllegalArgumentException("Unknown field '" + field + "'");
    }
}
