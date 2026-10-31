package net.quiwh.cwi.mixin.metallurgy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fr.lucreeper74.createmetallurgy.content.blocks.foundry_basin.FoundryBasinRecipe", remap = false)
abstract class FoundryBasinRecipeMixin {
    @Inject(method = "getMaxInputCount", at = @At("HEAD"), cancellable = true)
    private void cwi$stackedItemInputs(CallbackInfoReturnable<Integer> cir) {
        // Each ingredient entry consumes one item; four nine-item stacks can supply 36 entries.
        cir.setReturnValue(36);
    }
}
