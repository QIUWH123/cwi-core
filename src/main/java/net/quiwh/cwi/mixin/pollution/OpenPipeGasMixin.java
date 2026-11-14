package net.quiwh.cwi.mixin.pollution;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.quiwh.cwi.pollution.GasRelease;
import net.quiwh.cwi.pollution.GasProfiles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Endpoint only: connected inventories and all unrelated fluids keep native processing. */
@Mixin(targets="com.simibubi.create.content.fluids.OpenEndedPipe$OpenEndFluidHandler",remap=false)
abstract class OpenPipeGasMixin {
    @Shadow @Final OpenEndedPipe this$0;
    @Inject(method="fill",at=@At("HEAD"),cancellable=true)
    private void cwi$vent(FluidStack stack,FluidAction action,CallbackInfoReturnable<Integer> cir) {
        var gas=stack.isEmpty()?null:GasProfiles.fromFluid(stack.getFluid());
        if(gas==null) return;
        var direction=net.minecraft.core.Direction.getNearest(this$0.getOutputPos().getX()-this$0.getPos().getX(),
                this$0.getOutputPos().getY()-this$0.getPos().getY(),this$0.getOutputPos().getZ()-this$0.getPos().getZ());
        if(!(this$0.getWorld() instanceof ServerLevel level)) {
            cir.setReturnValue(0);return;
        }
        cir.setReturnValue(GasRelease.release(level,this$0.getPos(),direction,stack.copyWithAmount(Math.min(1000,stack.getAmount())),action));
    }
}
