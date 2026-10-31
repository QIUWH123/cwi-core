package net.quiwh.cwi.machine;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Shared persisted/synchronised storage; recipes use the internal handlers, automation uses side views. */
public class MachineBlockEntity extends KineticBlockEntity {
    private boolean reading;
    private boolean syncPending;
    private int syncCooldown;
    protected final ItemStackHandler inventory;
    protected final MultiTank tanks;

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, int... capacities) {
        super(type, pos, state);
        inventory = new ItemStackHandler(slots) {
            @Override protected void onContentsChanged(int slot) { onInventoryChanged(slot); changed(); }
            @Override public int getSlotLimit(int slot) { return itemSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return acceptsItem(slot, stack); }
        };
        tanks = new MultiTank(this::changed, capacities);
    }

    public ItemStackHandler items() { return inventory; }
    public MultiTank fluids() { return tanks; }
    public ItemStack getItem(int slot) { return inventory.getStackInSlot(slot); }
    public FluidStack getFluid(int tank) { return tanks.getFluidInTank(tank); }
    protected int itemSlotLimit(int slot) { return 64; }
    protected boolean acceptsItem(int slot, ItemStack stack) { return true; }
    protected void onInventoryChanged(int slot) {}

    public @Nullable IItemHandler getItemCapability(@Nullable Direction side) { return inventory.getSlots() == 0 ? null : inventory; }
    public @Nullable IFluidHandler getFluidCapability(@Nullable Direction side) { return tanks.getTanks() == 0 ? null : tanks; }

    protected void changed() {
        if (reading || level == null || level.isClientSide) return;
        setChanged();
        syncPending = true;
    }

    protected final boolean isReadingStorage() { return reading; }

    /** Dirty saves immediately; ordinary storage mutations share a packet at most every eight ticks. */
    @Override public void sendData() {
        syncPending = false;
        syncCooldown = 8;
        super.sendData();
    }

    /** Numeric requirements are independent of Create's configurable speed-tier labels. */
    public float minimumRequiredRpm() { return 0; }
    @Override public boolean isSpeedRequirementFulfilled() {
        return super.isSpeedRequirementFulfilled() && Math.abs(getSpeed()) >= minimumRequiredRpm();
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        boolean kinetic = super.addToGoggleTooltip(tooltip, sneaking);
        if (kinetic) tooltip.add(Component.empty());
        MachineGoggles.heading(tooltip, getBlockState().getBlock().getName());
        addMachineInfo(tooltip);
        MachineGoggles.contents(tooltip, inventory, tanks);
        return true;
    }

    protected void addMachineInfo(List<Component> tooltip) {
        float minimum = minimumRequiredRpm();
        if (minimum > 0) MachineGoggles.line(tooltip, Component.translatable("cwi.machine.minimum_rpm", MachineGoggles.number(minimum)));
        if ((inventory.getSlots() > 0 || tanks.getTanks() > 0) && getBlockState().hasProperty(MachineBlock.WORKING))
            MachineGoggles.line(tooltip, Component.translatable("cwi.machine.status", Component.translatable(
                    getBlockState().getValue(MachineBlock.WORKING) ? "cwi.machine.running" : "cwi.machine.idle")));
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("MachineDataVersion", 1);
        tag.put("Items", inventory.serializeNBT(registries));
        tag.put("Fluids", tanks.save(registries));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        reading = true;
        try {
            super.read(tag, registries, clientPacket);
            CompoundTag savedItems = tag.getCompound("Items").copy();
            savedItems.putInt("Size", inventory.getSlots());
            inventory.deserializeNBT(registries, savedItems);
            tanks.load(registries, tag.getCompound("Fluids"));
        } finally { reading = false; }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, stack.copy());
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    protected boolean hasWorkingSpeed(float minimum) {
        return MachineKinetics.canWork(getSpeed(), isOverStressed(), minimum,
                com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get());
    }

    @Override public void tick() {
        super.tick();
        // Create's RotationPropagator owns the configured overspeed limit and network destruction.
        if (level == null || level.isClientSide) return;
        if (syncCooldown > 0) syncCooldown--;
        if (syncPending && syncCooldown == 0) sendData();
    }
}
