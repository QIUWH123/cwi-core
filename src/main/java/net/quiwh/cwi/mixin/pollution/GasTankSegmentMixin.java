package net.quiwh.cwi.mixin.pollution;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.function.Consumer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.pollution.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=SmartFluidTankBehaviour.TankSegment.class,remap=false)
abstract class GasTankSegmentMixin {
    @Shadow @Final SmartFluidTankBehaviour this$0;
    @WrapOperation(method="<init>",at=@At(value="NEW",target="com/simibubi/create/foundation/fluid/SmartFluidTank"))
    private SmartFluidTank cwi$openStorage(int capacity,Consumer<FluidStack> callback,Operation<SmartFluidTank> original) {
        var owner=this$0.blockEntity;
        return GasContainers.supported(owner)?new GasVentingTank(owner,capacity,callback):original.call(capacity,callback);
    }
}
