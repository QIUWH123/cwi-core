package net.quiwh.cwi.mixin.vintage;

import com.simibubi.create.foundation.item.SmartInventory;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.compat.ProcessingOutputCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeBlockEntity", remap = false)
abstract class CentrifugeBlockEntityMixin {
    @Shadow public SmartInventory outputInv;

    @Inject(method = "acceptOutputs", at = @At("HEAD"), cancellable = true)
    private void cwi$reserveOutputs(List<ItemStack> items, List<FluidStack> fluids, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (simulate && !ProcessingOutputCapacity.canFit(outputInv, items)) cir.setReturnValue(false);
    }
}
