package net.quiwh.cwi.mixin.emissive;

import java.util.Arrays;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import net.quiwh.cwi.client.emissive.EmissiveTextures;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuadLighter.class)
public abstract class QuadLighterMixin {
    @Shadow @Final private float[] brightness;
    @Shadow @Final private int[] lightmap;
    @Inject(method = "computeLightingForQuad(Lnet/minecraft/client/renderer/block/model/BakedQuad;)V", at = @At("HEAD"), cancellable = true)
    private void cwi$unshadedLighting(BakedQuad quad, CallbackInfo ci) {
        if (!EmissiveTextures.isEmissive(quad)) return;
        Arrays.fill(brightness, 1);
        Arrays.fill(lightmap, LightTexture.FULL_BRIGHT);
        ci.cancel();
    }
}
