package net.quiwh.cwi.mixin.vintage;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.curving_press.CurvingPressRenderer", remap = false)
abstract class CurvingPressRedstoneRendererMixin {
    @ModifyExpressionValue(method = "renderSafe(Lcom/negodya1/vintageimprovements/content/kinetics/curving_press/CurvingPressBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "FIELD", target = "Lcom/negodya1/vintageimprovements/content/kinetics/curving_press/CurvingPressBlockEntity;redstoneModule:Z"))
    private boolean cwi$normalModel(boolean installed) { return false; }
}
