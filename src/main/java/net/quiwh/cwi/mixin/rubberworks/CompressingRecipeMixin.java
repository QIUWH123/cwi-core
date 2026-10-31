package net.quiwh.cwi.mixin.rubberworks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.hadron13.rubberworks.blocks.compressor.CompressingRecipe", remap = false)
abstract class CompressingRecipeMixin {
    @Inject(method = "getMaxOutputCount", at = @At("HEAD"), cancellable = true)
    private void cwi$outputLimit(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(6); }
}
