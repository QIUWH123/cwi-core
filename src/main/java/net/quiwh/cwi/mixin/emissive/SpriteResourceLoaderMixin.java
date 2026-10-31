package net.quiwh.cwi.mixin.emissive;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.quiwh.cwi.client.emissive.EmissiveMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpriteResourceLoader.class)
public interface SpriteResourceLoaderMixin {
    @ModifyReturnValue(method = "create", at = @At("RETURN"))
    private static SpriteResourceLoader cwi$retainEmissiveMetadata(SpriteResourceLoader original) {
        return (id, resource, constructor) -> original.loadSprite(id, resource, (name, size, image, retained) -> {
            Optional<EmissiveMetadata> section;
            try {
                section = resource.metadata().getSection(EmissiveMetadata.SERIALIZER);
            } catch (IOException | RuntimeException invalid) {
                LogUtils.getLogger().warn("Ignoring invalid CWI texture metadata for {}", id, invalid);
                section = Optional.empty();
            }
            var metadata = EmissiveMetadata.retain(retained, section);
            return constructor.create(name, size, image, metadata);
        });
    }
}
