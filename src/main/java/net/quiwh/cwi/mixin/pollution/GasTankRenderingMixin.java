package net.quiwh.cwi.mixin.pollution;

import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.pollution.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gas has no liquid surface, including retained contents awaiting an open outlet. */
@Mixin(value=SmartFluidTankBehaviour.TankSegment.class,remap=false)
abstract class GasTankRenderingMixin {
    @Shadow @Final SmartFluidTankBehaviour this$0;
    @Shadow protected FluidStack renderedFluid;
    private boolean cwi$gas(){return GasContainers.supported(this$0.blockEntity)&&!renderedFluid.isEmpty()&&GasProfiles.isGas(renderedFluid.getFluid());}
    @Inject(method="getRenderedFluid",at=@At("HEAD"),cancellable=true)
    private void cwi$noSurface(CallbackInfoReturnable<FluidStack> cir){if(cwi$gas())cir.setReturnValue(FluidStack.EMPTY);}
    @Inject(method="getTotalUnits",at=@At("HEAD"),cancellable=true)
    private void cwi$noLiquidHeight(float partialTicks,CallbackInfoReturnable<Float> cir){if(cwi$gas())cir.setReturnValue(0F);}
}
