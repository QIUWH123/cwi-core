package net.quiwh.cwi.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Internal, server-owned storage used for atomic recipes, never exposed as an external mutation API. */
public interface MutableRecipeFluids extends IFluidHandler {
    void setFluid(int tank, FluidStack stack);
    /** Fixed stage destination for a recipe result, or -1 for ordinary flexible storage. */
    default int outputTank(int resultIndex) { return -1; }
    default boolean canExtractTank(int tank) { return true; }
    default boolean canInsertTank(int tank, FluidStack stack) { return isFluidValid(tank, stack); }
}
