package net.quiwh.cwi.mixin.emissive;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.quiwh.cwi.client.emissive.EmissiveTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @ModifyArgs(method = "putQuadData", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private void cwi$unshadedVertices(Args args) {
        if (!EmissiveTextures.isEmissive((BakedQuad)args.get(1))) return;
        args.set(2, new float[]{1, 1, 1, 1});
        args.set(7, new int[]{LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT});
    }
}
