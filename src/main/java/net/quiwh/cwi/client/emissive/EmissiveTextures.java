package net.quiwh.cwi.client.emissive;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class EmissiveTextures {
    private EmissiveTextures() {}
    public static boolean isEmissive(TextureAtlasSprite sprite) {
        return sprite != null && sprite.contents().metadata().getSection(EmissiveMetadata.SERIALIZER)
                .map(EmissiveMetadata::emissive).orElse(false);
    }
    public static boolean isEmissive(BakedQuad quad) { return isEmissive(quad.getSprite()); }
}
