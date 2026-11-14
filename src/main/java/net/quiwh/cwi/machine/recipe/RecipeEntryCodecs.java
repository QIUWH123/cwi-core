package net.quiwh.cwi.machine.recipe;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Set;

/** Prevent native codecs from silently ignoring unsupported pool behavior. */
final class RecipeEntryCodecs {
    static <V> Codec<V> fields(Codec<V> codec, Set<String> fields) {
        return new Codec<>() {
            @Override public <T> DataResult<Pair<V,T>> decode(DynamicOps<T> ops, T input) {
                var map = ops.getMap(input).result();
                if (map.isPresent()) {
                    var unknown = map.get().entries().map(entry -> ops.getStringValue(entry.getFirst()).result().orElse(""))
                            .filter(key -> !fields.contains(key)).findFirst();
                    if (unknown.isPresent()) return DataResult.error(() -> "Unsupported pool entry field: " + unknown.get());
                }
                try { return codec.decode(ops, input); }
                catch (IllegalArgumentException failure) { return DataResult.error(failure::getMessage); }
            }
            @Override public <T> DataResult<T> encode(V input, DynamicOps<T> ops, T prefix) { return codec.encode(input, ops, prefix); }
        };
    }
    private RecipeEntryCodecs() {}
}
