package net.quiwh.cwi.compat.processing;

import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.quiwh.cwi.machine.MutableRecipeFluids;

/** Internal transaction view with separate input/output permissions. Never an external capability. */
public final class NativeStorage {
    public static IItemHandlerModifiable items(IItemHandlerModifiable input,IItemHandlerModifiable output){return new IItemHandlerModifiable(){
        public int getSlots(){return input.getSlots()+output.getSlots();}
        private boolean in(int s){return s<input.getSlots();}
        private int local(int s){return in(s)?s:s-input.getSlots();}
        private IItemHandlerModifiable handler(int s){return in(s)?input:output;}
        public ItemStack getStackInSlot(int s){return handler(s).getStackInSlot(local(s));}
        public void setStackInSlot(int s,ItemStack stack){handler(s).setStackInSlot(local(s),stack);}
        public int getSlotLimit(int s){return handler(s).getSlotLimit(local(s));}
        public boolean isItemValid(int s,ItemStack stack){return !in(s);}
        public ItemStack extractItem(int s,int n,boolean simulate){return in(s)?input.extractItem(s,n,simulate):ItemStack.EMPTY;}
        public ItemStack insertItem(int s,ItemStack stack,boolean simulate){return in(s)?stack:ItemHandlerHelper.insertItemStacked(output,stack,simulate);}
    };}
    public static IItemHandlerModifiable range(IItemHandlerModifiable handler,int start,int end){return new IItemHandlerModifiable(){
        public int getSlots(){return end-start;}
        public ItemStack getStackInSlot(int s){return handler.getStackInSlot(start+s);}
        public void setStackInSlot(int s,ItemStack stack){handler.setStackInSlot(start+s,stack);}
        public int getSlotLimit(int s){return handler.getSlotLimit(start+s);}
        public boolean isItemValid(int s,ItemStack stack){return true;}
        public ItemStack extractItem(int s,int n,boolean simulate){var stack=getStackInSlot(s);var result=stack.copyWithCount(Math.min(n,stack.getCount()));if(!simulate){var after=stack.copy();after.shrink(result.getCount());setStackInSlot(s,after);}return result;}
        public ItemStack insertItem(int s,ItemStack stack,boolean simulate){return handler.insertItem(start+s,stack,simulate);}
    };}
    public static MutableRecipeFluids fluids(SmartFluidTankBehaviour input,SmartFluidTankBehaviour output){return new MutableRecipeFluids(){
        private int inputs(){return input==null?0:input.getTanks().length;}
        public int getTanks(){return inputs()+(output==null?0:output.getTanks().length);}
        private SmartFluidTankBehaviour.TankSegment tank(int t){return t<inputs()?input.getTanks()[t]:output.getTanks()[t-inputs()];}
        public FluidStack getFluidInTank(int t){return ((net.quiwh.cwi.mixin.processing.TankSegmentAccess)tank(t)).cwi$tank().getFluid();}
        public int getTankCapacity(int t){return ((net.quiwh.cwi.mixin.processing.TankSegmentAccess)tank(t)).cwi$tank().getCapacity();}
        public boolean isFluidValid(int t,FluidStack stack){return t>=inputs();}
        public boolean canExtractTank(int t){return t<inputs();}
        public void setFluid(int t,FluidStack stack){((net.quiwh.cwi.mixin.processing.TankSegmentAccess)tank(t)).cwi$tank().setFluid(stack);}
        public int fill(FluidStack stack,FluidAction action){return 0;}
        public FluidStack drain(FluidStack stack,FluidAction action){return FluidStack.EMPTY;}
        public FluidStack drain(int amount,FluidAction action){return FluidStack.EMPTY;}
    };}
    public static MutableRecipeFluids routed(net.neoforged.neoforge.fluids.capability.templates.FluidTank input,
            java.util.List<? extends net.neoforged.neoforge.fluids.capability.templates.FluidTank> outputs,
            java.util.function.IntPredicate voidOverflow) {
        return new MutableRecipeFluids() {
            private net.neoforged.neoforge.fluids.capability.templates.FluidTank tank(int t){return t==0?input:outputs.get(t-1);}
            public int getTanks(){return 1+outputs.size();}
            public FluidStack getFluidInTank(int t){return tank(t).getFluid();}
            public int getTankCapacity(int t){return t>0&&voidOverflow.test(t-1)?Integer.MAX_VALUE:tank(t).getCapacity();}
            public boolean isFluidValid(int t,FluidStack stack){return t>0;}
            public boolean canExtractTank(int t){return t==0;}
            public int outputTank(int result){return result+1;}
            public void setFluid(int t,FluidStack stack){tank(t).setFluid(stack.copyWithAmount(Math.min(stack.getAmount(),tank(t).getCapacity())));}
            public int fill(FluidStack stack,FluidAction action){return 0;}
            public FluidStack drain(FluidStack stack,FluidAction action){return FluidStack.EMPTY;}
            public FluidStack drain(int amount,FluidAction action){return FluidStack.EMPTY;}
        };
    }
    public static final IItemHandlerModifiable EMPTY_ITEMS=new ItemStackHandler(0);
    public static final MutableRecipeFluids EMPTY_FLUIDS=fluids(null,null);
    private NativeStorage(){}
}
