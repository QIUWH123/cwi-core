package net.quiwh.cwi.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Dynamic capability proxies never retain access to a broken or reassigned controller. */
public final class FurnaceHatchBlockEntity extends BlockEntity {
    private final java.util.Set<BlockPos> owners = new java.util.TreeSet<>(java.util.Comparator.comparingLong(BlockPos::asLong));
    private final IItemHandler itemProxy = new IItemHandler() {
        private IItemHandler handler() { var c = controller(); return c == null ? null : c.hatchItems(); }
        public int getSlots() { var h = handler(); return h == null ? 0 : h.getSlots(); }
        public ItemStack getStackInSlot(int slot) { var h = handler(); return h == null ? ItemStack.EMPTY : h.getStackInSlot(slot); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { var h = handler(); return h == null ? stack : h.insertItem(slot,stack,simulate); }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { var h = handler(); return h == null ? ItemStack.EMPTY : h.extractItem(slot,amount,simulate); }
        public int getSlotLimit(int slot) { var h = handler(); return h == null ? 0 : h.getSlotLimit(slot); }
        public boolean isItemValid(int slot, ItemStack stack) { var h = handler(); return h != null && h.isItemValid(slot,stack); }
    };
    private final IFluidHandler fluidProxy = new IFluidHandler() {
        private IFluidHandler handler() { var c = controller(); return c == null ? null : c.hatchFluids(); }
        public int getTanks() { var h = handler(); return h == null ? 0 : h.getTanks(); }
        public FluidStack getFluidInTank(int tank) { var h = handler(); return h == null ? FluidStack.EMPTY : h.getFluidInTank(tank); }
        public int getTankCapacity(int tank) { var h = handler(); return h == null ? 0 : h.getTankCapacity(tank); }
        public boolean isFluidValid(int tank, FluidStack stack) { var h = handler(); return h != null && h.isFluidValid(tank,stack); }
        public int fill(FluidStack stack, FluidAction action) { var h = handler(); return h == null ? 0 : h.fill(stack,action); }
        public FluidStack drain(FluidStack stack, FluidAction action) { var h = handler(); return h == null ? FluidStack.EMPTY : h.drain(stack,action); }
        public FluidStack drain(int amount, FluidAction action) { var h = handler(); return h == null ? FluidStack.EMPTY : h.drain(amount,action); }
    };
    public FurnaceHatchBlockEntity(BlockPos pos, BlockState state) { super(MachineBlocks.FURNACE_HATCH_ENTITY.get(),pos,state); }
    public boolean availableTo(BlockPos controller) { return true; }
    public void claim(BlockPos controller) { if (owners.add(controller.immutable())) setChanged(); }
    public void release(BlockPos controller) { if (owners.remove(controller)) setChanged(); }
    private @Nullable BlastFurnaceBlockEntity controller() {
        if (level == null) return null;
        // Sharing never merges storage. Choose one loaded valid controller deterministically.
        for (BlockPos owner : java.util.List.copyOf(owners)) {
            if (!level.hasChunkAt(owner)) continue;
            if (level.getBlockEntity(owner) instanceof BlastFurnaceBlockEntity furnace && furnace.ownsHatch(worldPosition)) return furnace;
        }
        return null;
    }
    public IItemHandler getItemCapability(@Nullable Direction side) { return itemProxy; }
    public IFluidHandler getFluidCapability(@Nullable Direction side) { return fluidProxy; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag,registries); tag.putLongArray("Controllers",owners.stream().mapToLong(BlockPos::asLong).toArray()); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag,registries); owners.clear(); for (long pos : tag.getLongArray("Controllers")) owners.add(BlockPos.of(pos)); if (tag.contains("Controller")) owners.add(BlockPos.of(tag.getLong("Controller"))); }
}
