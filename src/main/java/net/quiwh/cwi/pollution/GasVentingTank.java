package net.quiwh.cwi.pollution;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.function.Consumer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/** Native storage when sealed; an atmosphere outlet when open. No temporary fill/sync for incoming gas. */
public final class GasVentingTank extends SmartFluidTank {
    private final BlockEntity owner;
    public GasVentingTank(BlockEntity owner,int capacity,Consumer<FluidStack> changed) {
        super(capacity,changed);this.owner=owner;
    }
    @Override public int fill(FluidStack stack,FluidAction action) {
        if(!stack.isEmpty()&&GasProfiles.fromFluid(stack.getFluid())!=null&&GasContainers.open(owner))
            return isFluidValid(stack)?GasRelease.release(owner,stack,action):0;
        return super.fill(stack,action);
    }
    @Override public void setFluid(FluidStack stack) {
        if(!stack.isEmpty()&&GasProfiles.fromFluid(stack.getFluid())!=null&&GasContainers.open(owner)) {
            int accepted=GasRelease.release(owner,stack,FluidAction.EXECUTE);
            stack=stack.copyWithAmount(stack.getAmount()-accepted);
            if(!stack.isEmpty())GasContainers.retry(owner);
            if(stack.isEmpty()&&isEmpty())return;
        }
        super.setFluid(stack);
    }
}
