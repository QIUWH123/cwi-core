package net.quiwh.cwi.mixin.emissive;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.quiwh.cwi.client.emissive.EmissiveTextures;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BakedQuad.class, priority = 1100)
public abstract class BakedQuadMixin {
    @Shadow @Final @Mutable protected int[] vertices;
    @Shadow @Final @Mutable private boolean shade;
    @Shadow @Final @Mutable private boolean hasAmbientOcclusion;

    @Inject(method = "<init>([IILnet/minecraft/core/Direction;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZ)V", at = @At("RETURN"))
    private void cwi$fullBright(CallbackInfo ci) {
        if (!EmissiveTextures.isEmissive((BakedQuad)(Object)this)) return;
        vertices = vertices.clone();
        for (int i = 0; i < 4; i++) vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.UV2] = LightTexture.FULL_BRIGHT;
        shade = false;
        hasAmbientOcclusion = false;
    }
}
