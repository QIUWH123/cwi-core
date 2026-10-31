package net.quiwh.cwi.mixin.emissive;

import java.util.Arrays;
import net.caffeinemc.mods.sodium.client.model.light.LightMode;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.fabricmc.fabric.api.renderer.v1.material.ShadeMode;
import net.minecraft.client.renderer.LightTexture;
import net.quiwh.cwi.client.emissive.EmissiveTextures;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public abstract class SodiumBlockRenderContextMixin {
    @Shadow @Final protected QuadLightData quadLightData;
    @Inject(method = "shadeQuad", at = @At("HEAD"), cancellable = true)
    private void cwi$unshadedSodium(MutableQuadViewImpl quad, LightMode mode, boolean emissive, ShadeMode shadeMode, CallbackInfo ci) {
        if (!EmissiveTextures.isEmissive(quad.cachedSprite())) return;
        Arrays.fill(quadLightData.br, 1);
        Arrays.fill(quadLightData.lm, LightTexture.FULL_BRIGHT);
        for (int i = 0; i < 4; i++) quad.lightmap(i, LightTexture.FULL_BRIGHT);
        ci.cancel();
    }
}
