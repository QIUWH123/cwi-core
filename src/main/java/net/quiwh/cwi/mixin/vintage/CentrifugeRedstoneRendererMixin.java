package net.quiwh.cwi.mixin.vintage;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeRenderer", remap = false)
abstract class CentrifugeRedstoneRendererMixin {
    @ModifyExpressionValue(method = "renderSafe(Lcom/negodya1/vintageimprovements/content/kinetics/centrifuge/CentrifugeBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lcom/negodya1/vintageimprovements/content/kinetics/centrifuge/CentrifugeBlockEntity;getRedstoneApp()Z"))
    private boolean cwi$normalModel(boolean comparatorAvailable) { return false; }
}
