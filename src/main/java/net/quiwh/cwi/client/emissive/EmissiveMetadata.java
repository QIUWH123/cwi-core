package net.quiwh.cwi.client.emissive;

import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;

/** Atlas metadata, independent of animation and owned by each loaded sprite. */
public record EmissiveMetadata(boolean emissive) {
    public static ResourceMetadata retain(ResourceMetadata original, Optional<EmissiveMetadata> emissive) {
        return new RetainedMetadata(original, emissive);
    }
    private record RetainedMetadata(ResourceMetadata original, Optional<EmissiveMetadata> emissive) implements ResourceMetadata {
        @Override @SuppressWarnings("unchecked")
        public <T> Optional<T> getSection(MetadataSectionSerializer<T> serializer) {
            return serializer == SERIALIZER ? (Optional<T>)(Optional<?>)emissive : original.getSection(serializer);
        }
    }
    public static final MetadataSectionSerializer<EmissiveMetadata> SERIALIZER = new MetadataSectionSerializer<>() {
        @Override public String getMetadataSectionName() { return "cwi"; }
        @Override public EmissiveMetadata fromJson(JsonObject json) {
            var value = json.get("emissive");
            return new EmissiveMetadata(value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean());
        }
    };
}
