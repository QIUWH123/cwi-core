package net.quiwh.cwi.mixin.pollution;

import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.quiwh.cwi.mixin.processing.TankSegmentAccess;
import net.quiwh.cwi.pollution.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Probe atmospheric capacity once for a whole multi-segment tank, after native insertion permissions. */
@Mixin(value=SmartFluidTankBehaviour.InternalFluidHandler.class,remap=false)
abstract class GasTankHandlerMixin {
    @Shadow @Final SmartFluidTankBehaviour this$0;
    @Inject(method={"fill","forceFill"},at=@At(value="INVOKE",target="Lcom/simibubi/create/foundation/fluid/CombinedTankWrapper;fill(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)I"),cancellable=true)
    private void cwi$oneOutlet(FluidStack stack,FluidAction action,CallbackInfoReturnable<Integer> cir) {
        if(stack.isEmpty()||!GasProfiles.isGas(stack.getFluid())||!GasContainers.open(this$0.blockEntity))return;
        for(var segment:this$0.getTanks())if(((TankSegmentAccess)segment).cwi$tank().isFluidValid(stack)) {
            cir.setReturnValue(GasRelease.release(this$0.blockEntity,stack,action));return;
        }
        cir.setReturnValue(0);
    }
}
