package net.quiwh.cwi.machine;

import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;
import org.jetbrains.annotations.Nullable;

public final class MixingVesselBlockEntity extends MachineBlockEntity {
    private ResourceLocation recipeId;
    private String fingerprint = "";
    private int progress, duration, batch;
    private float requiredRpm = 1;
    private int lastComparator = -1;
    private int feedbackCooldown;
    private final IItemHandler comparatorInventory = new net.neoforged.neoforge.items.wrapper.RangedWrapper(inventory, 0, 4);
    private final IItemHandler external = new IItemHandler() {
        public int getSlots() { return 6; }
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot < 4 ? inventory.insertItem(slot, stack, simulate) : stack; }
        public ItemStack extractItem(int slot, int count, boolean simulate) { return inventory.extractItem(slot, count, simulate); }
        public int getSlotLimit(int slot) { return 64; }
        public boolean isItemValid(int slot, ItemStack stack) { return slot < 4; }
    };
    public MixingVesselBlockEntity(BlockPos pos, BlockState state) { super(MachineBlocks.MIXING_VESSEL_ENTITY.get(), pos, state, 6, 1000, 1000, 1000); }
    @Override public @Nullable IItemHandler getItemCapability(@Nullable Direction side) { return external; }
    @Override public float minimumRequiredRpm() { return requiredRpm; }
    @Override protected void addMachineInfo(List<Component> tooltip) {
        super.addMachineInfo(tooltip);
        MachineGoggles.progress(tooltip, progress, duration);
        if (batch > 0) MachineGoggles.line(tooltip, Component.translatable("cwi.machine.batch", batch));
    }
    public int comparatorSignal() { return ItemHelper.calcRedstoneFromInventory(comparatorInventory); }
    @Override protected void onInventoryChanged(int slot) {
        if (isReadingStorage() || level == null || level.isClientSide) return;
        int signal = comparatorSignal();
        if (signal != lastComparator) {
            lastComparator = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    private void setRequiredRpm(float rpm) {
        if (requiredRpm != rpm) { requiredRpm = rpm; changed(); }
    }
    @Override protected void changed() {
        feedbackCooldown = 0;
        super.changed();
    }
    private static long ingredientCount(RecipeHolder<MachineRecipe> holder) {
        return holder.value().itemInputs().stream().mapToLong(MachineRecipe.ItemInput::count).sum();
    }
    private List<RecipeHolder<MachineRecipe>> matchingRecipes() {
        return level.getRecipeManager().getAllRecipesFor(MachineRecipes.MIXING.recipeType()).stream()
                .sorted(Comparator.<RecipeHolder<MachineRecipe>>comparingLong(MixingVesselBlockEntity::ingredientCount).reversed()
                        .thenComparing(recipe -> recipe.id().toString()))
                .filter(recipe -> RecipeTransaction.matches(recipe.value(), inventory, tanks, 1)).toList();
    }
    public int progress() { return progress; }
    public int duration() { return duration; }
    public int batch() { return batch; }
    public static int durationAtSpeed(int baseTicks, float rpm) {
        return MachineKinetics.mixerDuration(baseTicks, rpm);
    }
    private void clearRecipe() { recipeId = null; fingerprint = ""; progress = duration = batch = 0; changed(); }

    @Override public void tick() {
        super.tick();
        if (level == null || isRemoved()) return;
        if (level.isClientSide) {
            if (getBlockState().getValue(MachineBlock.WORKING) && hasWorkingSpeed(1)) {
                for (var particle : MachineEffects.mixingParticles(this)) {
                    var pos = particle.position(); var velocity = particle.velocity();
                    level.addParticle(particle.particle(),pos.x,pos.y,pos.z,velocity.x,velocity.y,velocity.z);
                }
            }
            return;
        }
        if (!hasWorkingSpeed(1)) {
            if (recipeId != null) clearRecipe();
            if (feedbackCooldown-- <= 0) {
                var candidates = matchingRecipes();
                setRequiredRpm(candidates.isEmpty() ? 1 : Math.max(1, candidates.getFirst().value().minimumRpm()));
                feedbackCooldown = 20;
            }
            setWorking(false); return;
        }
        RecipeHolder<MachineRecipe> holder = recipeId == null ? null : level.getRecipeManager().byKey(recipeId)
                .filter(recipe -> recipe.value() instanceof MachineRecipe && recipe.value().getType() == MachineRecipes.MIXING.recipeType())
                .map(recipe -> (RecipeHolder<MachineRecipe>)(RecipeHolder<?>)recipe).orElse(null);
        if (recipeId != null && (holder == null || !holder.value().fingerprint(level.registryAccess()).equals(fingerprint))) { clearRecipe(); holder = null; }
        if (holder == null) {
            var candidates = matchingRecipes();
            holder = candidates.stream().filter(recipe -> hasWorkingSpeed(Math.max(1, recipe.value().minimumRpm()))).findFirst().orElse(null);
            setRequiredRpm(holder != null ? Math.max(1, holder.value().minimumRpm())
                    : candidates.isEmpty() ? 1 : Math.max(1, candidates.getFirst().value().minimumRpm()));
            if (holder == null) { setWorking(false); return; }
            recipeId = holder.id(); fingerprint = holder.value().fingerprint(level.registryAccess());
            duration = durationAtSpeed(holder.value().getProcessingDuration(), getSpeed());
            batch = RecipeTransaction.maxBatch(holder.value(), inventory, tanks, 4); progress = 0; changed();
        }
        MachineRecipe recipe = holder.value();
        if (!hasWorkingSpeed(Math.max(1, recipe.minimumRpm())) || !RecipeTransaction.matches(recipe, inventory, tanks, batch)) { clearRecipe(); setWorking(false); return; }
        boolean finishing = progress + 1 >= duration;
        boolean startsWithFluid = progress == 0 && java.util.stream.IntStream.range(0,tanks.getTanks())
                .anyMatch(tank -> !getFluid(tank).isEmpty());
        boolean committed = finishing ? RecipeTransaction.finishTick(recipe, inventory, tanks, batch, level.random)
                : RecipeTransaction.tick(recipe, inventory, tanks, batch, level.random);
        if (!committed) { clearRecipe(); setWorking(false); return; }
        if (startsWithFluid) level.playSound(null,worldPosition,SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                SoundSource.BLOCKS,.75f,Math.abs(getSpeed())<65?.75f:1.5f);
        setWorking(true);
        if (finishing) clearRecipe();
        else { progress++; setChanged(); if (progress % 20 == 0) sendData(); }
    }
    private void setWorking(boolean working) {
        if (level != null && getBlockState().getValue(MachineBlock.WORKING) != working) {
            level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.WORKING, working), 3);
            sendData();
        }
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.write(tag, registries, client);
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
        tag.putString("Fingerprint", fingerprint); tag.putInt("Progress", progress); tag.putInt("Duration", duration); tag.putInt("Batch", batch);
        tag.putFloat("RequiredRpm", requiredRpm);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.read(tag, registries, client);
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        fingerprint = tag.getString("Fingerprint"); duration = Math.max(1, tag.getInt("Duration")); progress = Math.clamp(tag.getInt("Progress"), 0, duration); batch = Math.clamp(tag.getInt("Batch"), 1, 4);
        requiredRpm = Float.isFinite(tag.getFloat("RequiredRpm")) ? Math.max(1, tag.getFloat("RequiredRpm")) : 1;
    }
}
