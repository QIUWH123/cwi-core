package net.quiwh.cwi.mixin.vintage;

import com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingRecipe;
import com.simibubi.create.foundation.item.SmartInventory;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.compat.ProcessingOutputCapacity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingTableBlockEntity", remap = false)
abstract class VibratingTableBlockEntityMixin {
    @Shadow public SmartInventory outputInv;
    @Shadow private VibratingRecipe lastRecipe;
    @Shadow boolean lastRecipeIsAssembly;

    // This second read follows recipe selection and precedes ordinary input consumption.
    @Inject(method = "process", at = @At(value = "FIELD",
            target = "Lcom/negodya1/vintageimprovements/content/kinetics/vibration/VibratingTableBlockEntity;lastRecipeIsAssembly:Z",
            opcode = Opcodes.GETFIELD, ordinal = 1), cancellable = true)
    private void cwi$reserveOrdinaryOutputs(CallbackInfo ci) {
        if (!lastRecipeIsAssembly && !ProcessingOutputCapacity.canFitRecipe(outputInv, lastRecipe)) ci.cancel();
    }

    @Inject(method = "acceptOutputs", at = @At("HEAD"), cancellable = true)
    private void cwi$reserveAssemblyOutputs(List<ItemStack> outputs, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (simulate && !ProcessingOutputCapacity.canFit(outputInv, outputs)) cir.setReturnValue(false);
    }
}
