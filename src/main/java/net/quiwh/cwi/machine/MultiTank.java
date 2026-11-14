package net.quiwh.cwi.machine;

import java.util.function.BiPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Fixed, independently addressable tanks. All mutation passes through the owner callback. */
public final class MultiTank implements MutableRecipeFluids {
    private final FluidTank[] tanks;
    private final Runnable changed;
    private BiPredicate<Integer, FluidStack> validator = (tank, fluid) -> true;

    public MultiTank(Runnable changed, int... capacities) {
        this(changed, capacities, (capacity, callback) -> new FluidTank(capacity) {
            @Override protected void onContentsChanged() { callback.run(); }
        });
    }

    public MultiTank(Runnable changed, int[] capacities, java.util.function.BiFunction<Integer, Runnable, FluidTank> factory) {
        this.changed = changed;
        tanks = new FluidTank[capacities.length];
        for (int i = 0; i < capacities.length; i++) {
            final int index = i;
            tanks[i] = factory.apply(capacities[i], changed);
            tanks[i].setValidator(stack -> validator.test(index, stack));
        }
    }

    public void setValidator(BiPredicate<Integer, FluidStack> validator) { this.validator = validator; }
    public FluidTank tank(int tank) { return tanks[tank]; }
    @Override public int getTanks() { return tanks.length; }
    @Override public FluidStack getFluidInTank(int tank) { return tanks[tank].getFluid(); }
    @Override public int getTankCapacity(int tank) { return tanks[tank].getCapacity(); }
    @Override public boolean isFluidValid(int tank, FluidStack fluid) { return tanks[tank].isFluidValid(fluid); }
    public void setFluid(int tank, FluidStack fluid) {
        if (!fluid.isEmpty() && (!isFluidValid(tank, fluid) || fluid.getAmount() > getTankCapacity(tank)))
            throw new IllegalArgumentException("Invalid fluid for tank " + tank);
        tanks[tank].setFluid(fluid.copy());
        changed.run();
    }

    @Override public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        int remaining = resource.getAmount();
        // Prefer an existing matching tank before claiming an empty one.
        for (boolean empty : new boolean[]{false, true}) {
            for (FluidTank tank : tanks) {
                if (tank.isEmpty() != empty) continue;
                remaining -= tank.fill(resource.copyWithAmount(remaining), action);
                if (remaining == 0) return resource.getAmount();
            }
        }
        return resource.getAmount() - remaining;
    }

    @Override public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        int drained = 0;
        for (FluidTank tank : tanks) {
            drained += tank.drain(resource.copyWithAmount(resource.getAmount() - drained), action).getAmount();
            if (drained == resource.getAmount()) break;
        }
        return drained == 0 ? FluidStack.EMPTY : resource.copyWithAmount(drained);
    }

    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        for (FluidTank tank : tanks) {
            if (!tank.isEmpty()) return drain(tank.getFluid().copyWithAmount(maxDrain), action);
        }
        return FluidStack.EMPTY;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < tanks.length; i++) tag.put("Tank" + i, tanks[i].writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        for (int i = 0; i < tanks.length; i++) {
            tanks[i].readFromNBT(registries, tag.getCompound("Tank" + i));
            FluidStack fluid = tanks[i].getFluid();
            if (!fluid.isEmpty() && !isFluidValid(i, fluid)) tanks[i].setFluid(FluidStack.EMPTY);
            else if (fluid.getAmount() > tanks[i].getCapacity()) tanks[i].setFluid(fluid.copyWithAmount(tanks[i].getCapacity()));
        }
    }
}
