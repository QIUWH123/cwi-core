package net.quiwh.cwi.mixin.vintage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingRecipe", remap = false)
abstract class VibratingRecipeMixin {
    @Inject(method = "getMaxOutputCount", at = @At("HEAD"), cancellable = true)
    private void cwi$outputLimit(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(9); }
}
