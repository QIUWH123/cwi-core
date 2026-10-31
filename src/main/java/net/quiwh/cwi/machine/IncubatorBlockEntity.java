package net.quiwh.cwi.machine;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;
import org.jetbrains.annotations.Nullable;

/** Each visible slot owns its own clock; replacing/removing its plate always clears that clock. */
public final class IncubatorBlockEntity extends MachineBlockEntity {
    private static final TagKey<Item> DISHES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cwi", "petri_dish"));
    private final Lane[] lanes = new Lane[8];
    private int feedbackTicks;
    private final IItemHandler external = new IItemHandler() {
        public int getSlots() { return 16; }
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot < 8 ? inventory.insertItem(slot, stack, simulate) : stack; }
        public ItemStack extractItem(int slot, int count, boolean simulate) { return inventory.extractItem(slot, count, simulate); }
        public int getSlotLimit(int slot) { return 1; }
        public boolean isItemValid(int slot, ItemStack stack) { return slot < 8 && acceptsItem(slot, stack); }
    };

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(MachineBlocks.INCUBATOR_ENTITY.get(), pos, state, 16);
        Arrays.setAll(lanes, index -> new Lane());
    }
    @Override protected int itemSlotLimit(int slot) { return 1; }
    @Override protected boolean acceptsItem(int slot, ItemStack stack) { return slot >= 8 || stack.is(DISHES); }
    @Override protected void onInventoryChanged(int slot) {
        if (slot < 8 && lanes != null && lanes[slot] != null) lanes[slot].clear();
    }
    @Override public @Nullable IItemHandler getItemCapability(@Nullable Direction side) { return external; }
    @Override public float minimumRequiredRpm() {
        float minimum = Float.POSITIVE_INFINITY;
        for (int i = 0; i < lanes.length; i++) if (!inventory.getStackInSlot(i).isEmpty() && lanes[i].hasCandidate)
            minimum = Math.min(minimum, lanes[i].requiredRpm);
        return Float.isFinite(minimum) ? minimum : 64;
    }
    @Override protected void addMachineInfo(List<Component> tooltip) {
        super.addMachineInfo(tooltip);
        for (int i = 0; i < lanes.length; i++) if (lanes[i].active())
            MachineGoggles.line(tooltip, Component.translatable("cwi.machine.lane_progress", i + 1,
                    Math.clamp(100L * lanes[i].progress / Math.max(1, lanes[i].duration), 0, 100)));
    }
    public int progress(int lane) { return lanes[lane].progress; }
    public int duration(int lane) { return lanes[lane].duration; }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isRemoved()) return;
        feedbackTicks++;
        boolean working = false;
        boolean progressUpdate = false;
        for (int index = 0; index < lanes.length; index++) {
            Lane lane = lanes[index];
            ItemStack plate = inventory.getStackInSlot(index);
            if (plate.isEmpty()) { if (lane.active()) { lane.clear(); changed(); } continue; }
            // Keep feedback current without scanning every recipe in eight stopped lanes each tick.
            if (!hasWorkingSpeed(64) && !lane.active() && !lane.requirementDirty && feedbackTicks % 20 != 0) continue;
            IItemHandlerModifiable view = laneView(index);
            RecipeHolder<MachineRecipe> holder = lane.recipeId == null ? null : level.getRecipeManager().byKey(lane.recipeId)
                    .filter(recipe -> recipe.value() instanceof MachineRecipe && recipe.value().getType() == MachineRecipes.INCUBATING.recipeType())
                    .map(recipe -> (RecipeHolder<MachineRecipe>)(RecipeHolder<?>)recipe).orElse(null);
            if (lane.active() && (holder == null || !ItemStack.matches(plate, lane.plate)
                    || !holder.value().fingerprint(level.registryAccess()).equals(lane.fingerprint))) {
                lane.clear(); changed(); holder = null;
            }
            if (holder == null) {
                var candidates = level.getRecipeManager().getAllRecipesFor(MachineRecipes.INCUBATING.recipeType()).stream()
                        .sorted(Comparator.comparing(recipe -> recipe.id().toString()))
                        .filter(recipe -> plateRecipe(recipe.value()) && RecipeTransaction.matches(recipe.value(), view, null, 1)).toList();
                holder = candidates.stream().filter(recipe -> hasWorkingSpeed(Math.max(64, recipe.value().minimumRpm()))).findFirst().orElse(null);
                float required = holder != null ? Math.max(64, holder.value().minimumRpm())
                        : candidates.isEmpty() ? 64 : Math.max(64, candidates.getFirst().value().minimumRpm());
                if (lane.hasCandidate != !candidates.isEmpty()) { lane.hasCandidate = !candidates.isEmpty(); changed(); }
                if (lane.requiredRpm != required) { lane.requiredRpm = required; changed(); }
                lane.requirementDirty = false;
                if (holder == null) continue;
                lane.recipeId = holder.id(); lane.plate = plate.copy(); lane.duration = Math.max(1, holder.value().getProcessingDuration());
                lane.fingerprint = holder.value().fingerprint(level.registryAccess()); lane.progress = 0; changed();
            }
            MachineRecipe recipe = holder.value();
            if (!hasWorkingSpeed(Math.max(64, recipe.minimumRpm())) || !RecipeTransaction.matches(recipe, view, null, 1)) {
                lane.clear(); lane.requiredRpm = Math.max(64, recipe.minimumRpm()); lane.hasCandidate = true; changed(); continue;
            }
            boolean finishing = lane.progress + 1 >= lane.duration;
            boolean committed = finishing ? RecipeTransaction.finishTick(recipe, view, null, 1, level.random)
                    : RecipeTransaction.tick(recipe, view, null, 1, level.random);
            if (!committed) { lane.clear(); changed(); continue; }
            working = true;
            if (finishing) {
                lane.clear(); changed();
            } else {
                lane.progress++;
                setChanged();
                if (lane.progress % 20 == 0) progressUpdate = true;
            }
        }
        if (getBlockState().getValue(MachineBlock.WORKING) != working) {
            level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.WORKING, working), 3);
            sendData();
        } else if (progressUpdate) sendData();
    }

    private static boolean plateRecipe(MachineRecipe recipe) {
        return recipe.fluidInputs().isEmpty() && recipe.fluidOutputs().isEmpty()
                && recipe.itemInputs().size() == 1 && !recipe.itemInputs().getFirst().perTick()
                && recipe.itemInputs().getFirst().count() == 1;
    }
    private IItemHandlerModifiable laneView(int index) {
        return new IItemHandlerModifiable() {
            private int actual(int slot) { return slot == 8 ? index : slot + 8; }
            public int getSlots() { return 9; }
            public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(actual(slot)); }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return inventory.insertItem(actual(slot), stack, simulate); }
            public ItemStack extractItem(int slot, int count, boolean simulate) { return slot == 8 ? inventory.extractItem(index, count, simulate) : ItemStack.EMPTY; }
            public int getSlotLimit(int slot) { return 1; }
            public boolean isItemValid(int slot, ItemStack stack) { return acceptsItem(actual(slot), stack); }
            public void setStackInSlot(int slot, ItemStack stack) { inventory.setStackInSlot(actual(slot), stack); }
        };
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.write(tag, registries, client);
        for (int index = 0; index < lanes.length; index++) {
            Lane lane = lanes[index]; CompoundTag state = new CompoundTag();
            state.putFloat("RequiredRpm", lane.requiredRpm);
            state.putBoolean("HasCandidate", lane.hasCandidate);
            if (lane.active()) { state.putString("Recipe", lane.recipeId.toString()); state.put("Plate", lane.plate.save(registries));
                state.putInt("Progress", lane.progress); state.putInt("Duration", lane.duration); state.putString("Fingerprint", lane.fingerprint); }
            tag.put("Lane" + index, state);
        }
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.read(tag, registries, client);
        for (int index = 0; index < lanes.length; index++) {
            CompoundTag state = tag.getCompound("Lane" + index); Lane lane = lanes[index]; lane.clear();
            lane.requiredRpm = Float.isFinite(state.getFloat("RequiredRpm")) ? Math.max(64, state.getFloat("RequiredRpm")) : 64;
            lane.hasCandidate = state.getBoolean("HasCandidate") || state.contains("Recipe");
            if (state.contains("Recipe")) {
                lane.recipeId = ResourceLocation.tryParse(state.getString("Recipe")); lane.plate = ItemStack.parseOptional(registries, state.getCompound("Plate"));
                lane.duration = Math.max(1, state.getInt("Duration")); lane.progress = Math.clamp(state.getInt("Progress"), 0, lane.duration);
                lane.fingerprint = state.getString("Fingerprint");
                if (!ItemStack.matches(lane.plate, inventory.getStackInSlot(index))) lane.clear();
            }
        }
    }
    private static final class Lane {
        ResourceLocation recipeId; ItemStack plate = ItemStack.EMPTY; int progress, duration; String fingerprint = ""; float requiredRpm = 64; boolean requirementDirty = true, hasCandidate;
        boolean active() { return recipeId != null; }
        void clear() { recipeId = null; plate = ItemStack.EMPTY; progress = duration = 0; fingerprint = ""; requiredRpm = 64; requirementDirty = true; hasCandidate = false; }
    }
}
