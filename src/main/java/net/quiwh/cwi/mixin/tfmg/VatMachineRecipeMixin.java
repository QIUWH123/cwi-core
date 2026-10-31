package net.quiwh.cwi.mixin.tfmg;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.drmangotea.tfmg.recipes.VatMachineRecipe", remap = false)
abstract class VatMachineRecipeMixin {
    @Inject(method = {"getMaxInputCount", "getMaxOutputCount"}, at = @At("HEAD"), cancellable = true)
    private void cwi$itemEntryLimit(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(8); }
}
