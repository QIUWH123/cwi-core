package net.quiwh.cwi.machine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;
import org.jetbrains.annotations.Nullable;

public final class BlastFurnaceBlockEntity extends MachineBlockEntity {
    private static final TagKey<Block> BRICKS = blockTag("fireproof_bricks"), CAST = blockTag("cast_iron_reinforcement"), STEEL = blockTag("steel_reinforcement"), REINFORCED = blockTag("reinforced_fireproof_bricks");
    private static final TagKey<Fluid> GASES = TagKey.create(Registries.FLUID, id("gases"));
    private static final TagKey<Item> COKE = TagKey.create(Registries.ITEM, id("coke"));
    private static final TagKey<Block> PASSIVE = TagKey.create(Registries.BLOCK, ResourceLocation.parse("create:passive_boiler_heaters"));
    private double temperature;
    private boolean formed, working, validating;
    private int armor, parallel, height, progress, duration, batch;
    private VesselOperation operation;
    private long lastScan = Long.MIN_VALUE;
    private Set<BlockPos> hatches = Set.of();
    private ResourceLocation recipeId;
    private String fingerprint = "";
    private final RecipeCandidates candidates = new RecipeCandidates();
    private final FurnaceStructure.Scanner structureScanner = new FurnaceStructure.Scanner();

    private final IItemHandlerModifiable recipeItems = new IItemHandlerModifiable() {
        public int getSlots() { return 14; }
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return slot < 8 ? inventory.extractItem(slot,amount,simulate) : ItemStack.EMPTY; }
        public int getSlotLimit(int slot) { return 64; }
        public boolean isItemValid(int slot, ItemStack stack) { return slot >= 8; }
        public void setStackInSlot(int slot, ItemStack stack) { inventory.setStackInSlot(slot,stack); }
    };
    private final MutableRecipeFluids recipeFluids = new FluidView(0,6,true,true) {
        @Override public boolean canExtractTank(int tank) { return tank < 4; }
        @Override public boolean canInsertTank(int tank, FluidStack stack) { return tank >= 4; }
    };
    private final IItemHandler hatchItems = new IItemHandler() {
        public int getSlots() { return 14; }
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot < 8 ? inventory.insertItem(slot, stack, simulate) : stack; }
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return inventory.extractItem(slot, amount, simulate); }
        public int getSlotLimit(int slot) { return 64; }
        public boolean isItemValid(int slot, ItemStack stack) { return slot < 8 && acceptsItem(slot, stack); }
    };
    private final IFluidHandler inputFluids = new FluidView(0,4,true,true);
    private final IFluidHandler outputFluids = new FluidView(4,2,false,true);

    public BlastFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(MachineBlocks.BLAST_FURNACE_ENTITY.get(), pos, state, 14, 3000,3000,3000,3000,3000,3000);
        tanks.setValidator((tank,fluid) -> tank >= 4 || (tank >= 2) == fluid.is(GASES));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("cwi",path); }
    private static TagKey<Block> blockTag(String name) { return TagKey.create(Registries.BLOCK,id(name)); }
    public boolean formed() { return formed; }
    public boolean working() { return working; }
    public int armor() { return armor; }
    public int parallel() { return parallel; }
    public int height() { return height; }
    public int progress() { return progress; }
    public int duration() { return duration; }
    public double temperature() { return temperature; }
    public IItemHandler hatchItems() { return hatchItems; }
    @Override protected int storageVersion() { return 2; }
    public IFluidHandler hatchFluids() { return inputFluids; }
    @Override public void setBlockState(BlockState state) {
        Direction previousFacing = getBlockState().getValue(MachineBlock.FACING);
        super.setBlockState(state);
        if (previousFacing != state.getValue(MachineBlock.FACING)) invalidateCapabilities();
    }
    @Override public @Nullable IItemHandler getItemCapability(@Nullable Direction side) { return null; }
    @Override public @Nullable IFluidHandler getFluidCapability(@Nullable Direction side) {
        return side == getBlockState().getValue(MachineBlock.FACING) ? outputFluids : null;
    }
    @Override public float calculateStressApplied() { lastStressApplied = 0; return 0; }
    @Override protected boolean acceptsItem(int slot, ItemStack stack) {
        boolean coke = stack.is(COKE) || Set.of("tfmg:coal_coke", "tfmg:coal_coke_dust", "tfmg:coal_coke_block").contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return slot < 8 && (slot >= 4) == coke;
    }

    private BlockPos at(int right, int up, int back) {
        Direction front = getBlockState().getValue(MachineBlock.FACING);
        return worldPosition.relative(front.getClockWise(),right).above(up).relative(front.getOpposite(),back);
    }
    private FurnaceStructure.Cell classify(int r,int u,int b) {
        BlockPos pos = at(r,u,b);
        if (!level.hasChunkAt(pos)) return FurnaceStructure.Cell.OTHER;
        BlockState state = level.getBlockState(pos);
        if (pos.equals(worldPosition)) return FurnaceStructure.Cell.CONTROLLER;
        if (state.isAir()) return FurnaceStructure.Cell.AIR;
        if (state.is(MachineBlocks.FURNACE_HATCH.get())) return FurnaceStructure.Cell.HATCH;
        if (state.is(STEEL)) return FurnaceStructure.Cell.STEEL;
        if (state.is(CAST)) return FurnaceStructure.Cell.CAST;
        if (state.is(BRICKS)) return FurnaceStructure.Cell.BRICK;
        if (state.is(REINFORCED)) return FurnaceStructure.Cell.REINFORCED_BRICK;
        return FurnaceStructure.Cell.OTHER;
    }
    public boolean ownsHatch(BlockPos pos) {
        validateStructure();
        return formed && hatches.contains(pos);
    }
    private void validateStructure() {
        if (level == null || level.isClientSide || validating || lastScan == level.getGameTime()) return;
        validating = true;
        try {
            lastScan = level.getGameTime();
            var match = structureScanner.scan(this::classify);
            Set<BlockPos> next = new HashSet<>();
            if (match != null) for (var offset : match.hatches()) {
                BlockPos pos = at(offset.right(),offset.up(),offset.back());
                if (!(level.getBlockEntity(pos) instanceof FurnaceHatchBlockEntity hatch) || !hatch.availableTo(worldPosition)) { match = null; break; }
                next.add(pos);
            }
            if (match == null) next.clear();
            for (BlockPos pos : hatches) if (!next.contains(pos) && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FurnaceHatchBlockEntity hatch) hatch.release(worldPosition);
            for (BlockPos pos : next) if (level.getBlockEntity(pos) instanceof FurnaceHatchBlockEntity hatch) hatch.claim(worldPosition);
            boolean nextFormed = match != null;
            int nextArmor = nextFormed ? match.armor() : 0, nextParallel = nextFormed ? match.parallel() : 0;
            if (formed != nextFormed || armor != nextArmor || parallel != nextParallel
                    || height != (nextFormed ? match.height() : 0) || !hatches.equals(next)) {
                formed = nextFormed; armor = nextArmor; parallel = nextParallel; height = nextFormed ? match.height() : 0;
                hatches = Set.copyOf(next); if (!formed) clearRecipe(); changed();
            }
        } finally { validating = false; }
    }
    private int heaterPoints() {
        BlockPos center = at(0,-2,1);
        int points = 0;
        for (BlockPos pos : List.of(center,center.north(),center.south(),center.east(),center.west())) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
                points += switch(state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) { case SMOULDERING -> 1; case KINDLED -> 2; case SEETHING -> 3; default -> 0; };
            } else if (state.is(PASSIVE) && (!state.hasProperty(CampfireBlock.LIT) || state.getValue(CampfireBlock.LIT))) points++;
        }
        return points;
    }
    private boolean heatMatches(MachineRecipe recipe) {
        int required = recipe.getRequiredHeat() == HeatCondition.SUPERHEATED ? 1900 : recipe.getRequiredHeat() == HeatCondition.HEATED ? 1200 : 0;
        return temperature >= Math.max(required,recipe.minimumHeat()) && temperature <= recipe.maximumHeat();
    }
    private void clearRecipe() { operation = null; recipeId = null; fingerprint = ""; progress = duration = batch = 0; setChanged(); }
    private void setWorking(boolean value) {
        working = value;
        if (level != null && getBlockState().getValue(MachineBlock.WORKING) != value) {
            level.setBlock(worldPosition,getBlockState().setValue(MachineBlock.WORKING,value),3);
            sendData();
        }
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isRemoved()) return;
        validateStructure();
        if (level.getGameTime() % 10 == 0) { temperature = FurnaceHeat.approach(temperature,formed ? heaterPoints() : 0,armor); changed(); }
        if (!formed) { setWorking(false); return; }
        if (level.getGameTime() % 5 == 0) { collectItems(); outputMolten(); }
        RecipeHolder<MachineRecipe> holder = recipeId == null ? null : level.getRecipeManager().byKey(recipeId)
                .filter(entry -> entry.value() instanceof MachineRecipe).map(entry -> (RecipeHolder<MachineRecipe>)(RecipeHolder<?>)entry).orElse(null);
        if (recipeId != null && (holder == null || holder.value().getType() != MachineRecipes.BLAST_FURNACE.recipeType()
                || !holder.value().fingerprint(level.registryAccess()).equals(fingerprint))) { clearRecipe(); holder = null; }
        if (holder == null) {
            holder = candidates.find(level.getRecipeManager(), MachineRecipes.BLAST_FURNACE.recipeType(), recipeItems, recipeFluids)
                    .stream().filter(entry -> heatMatches(entry.value())).findFirst().orElse(null);
            if (holder == null) { setWorking(false); return; }
            recipeId = holder.id(); fingerprint = holder.value().fingerprint(level.registryAccess());
            batch = parallel;
            while (batch > 0 && !VesselTransaction.eligible(holder.value(), recipeItems, recipeFluids, (ServerLevel)level, batch)) batch--;
            if (batch == 0) { clearRecipe(); setWorking(false); return; }
            operation = new VesselOperation(level.random.nextLong());
            duration = Math.max(1,(int)Math.floor(holder.value().getProcessingDuration() * FurnaceHeat.durationFactor(temperature))); progress = 0; changed();
        }
        var recipe = holder.value();
        if (!heatMatches(recipe)) { setWorking(false); return; }
        operation.prepare(duration);
        boolean finishing = operation.finishing();
        if (!VesselTransaction.step(recipe, operation, recipeItems, recipeFluids, (ServerLevel)level, batch)) {
            setChanged(); setWorking(false); return;
        }
        setWorking(true); progress++; setChanged();
        if (temperature >= 60 && level.getGameTime() % 4 == 0 && level instanceof ServerLevel server) {
            BlockPos chimney = at(0,height - 2,1);
            double multiplier=Math.sqrt(temperature/200);
            var random=level.random;
            var center=net.minecraft.world.phys.Vec3.atLowerCornerOf(chimney).add(.5,0,.5);
            MachineEffects.directed(server,ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    center.add(-.2+random.nextDouble()*.4,0,-.2+random.nextDouble()*.4),
                    new net.minecraft.world.phys.Vec3(-.05+random.nextDouble()*.1,
                            .1+random.nextDouble()*(.1*multiplier-.1),-.05+random.nextDouble()*.1),.3);
            var flame=MachineEffects.furnaceParticle(temperature);
            if(flame!=null) for(int i=0;i<multiplier;i++)
                MachineEffects.directed(server,flame,center,
                        new net.minecraft.world.phys.Vec3(-.1+random.nextDouble()*.2,
                                .1+random.nextDouble()*(.4*multiplier-.1),-.1+random.nextDouble()*.2),.3);
        }
        if (finishing) { clearRecipe(); changed(); }
        else if (progress % 20 == 0) sendData();
    }
    @Override protected AABB createRenderBoundingBox() { return new AABB(worldPosition).inflate(1); }
    private void collectItems() {
        BlockPos back = at(0,0,1);
        int[] remainingBudget = {64,64};
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class,new AABB(back),item -> item.isAlive() && !item.getItem().isEmpty())) {
            int kind = acceptsItem(4,entity.getItem()) ? 1 : 0;
            int offered = Math.min(remainingBudget[kind],entity.getItem().getCount());
            if (offered == 0) continue;
            ItemStack remaining = entity.getItem().copyWithCount(offered);
            for (int slot = kind * 4; slot < kind * 4 + 4 && !remaining.isEmpty(); slot++) remaining = inventory.insertItem(slot,remaining,false);
            int inserted = offered - remaining.getCount();
            if (inserted == 0) continue;
            remainingBudget[kind] -= inserted;
            ItemStack left = entity.getItem().copy(); left.shrink(inserted);
            if (left.isEmpty()) entity.discard(); else entity.setItem(left);
        }
    }
    private void outputMolten() {
        Direction front = getBlockState().getValue(MachineBlock.FACING);
        BlockPos targetPos = worldPosition.relative(front);
        if (!level.hasChunkAt(targetPos)) return;
        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK,targetPos,front.getOpposite());
        for (int tank = 4; tank < 6; tank++) {
            FluidStack fluid = tanks.getFluidInTank(tank);
            if (fluid.isEmpty()) continue;
            if (target != null) {
                int accepted = target.fill(fluid.copy(),IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) { int inserted = target.fill(fluid.copyWithAmount(accepted),IFluidHandler.FluidAction.EXECUTE); tanks.tank(tank).drain(inserted,IFluidHandler.FluidAction.EXECUTE); }
            } else if (level.isEmptyBlock(targetPos) && fluid.getAmount() >= 1000 && !fluid.getFluid().defaultFluidState().createLegacyBlock().isAir()) {
                if (level.setBlock(targetPos,fluid.getFluid().defaultFluidState().createLegacyBlock(),3)) tanks.tank(tank).drain(1000,IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }
    @Override protected void addMachineInfo(List<Component> tooltip) {
        super.addMachineInfo(tooltip);
        MachineGoggles.line(tooltip, Component.translatable("cwi.furnace.status",formed ? Component.translatable("cwi.machine.formed") : Component.translatable("cwi.machine.unformed")));
        MachineGoggles.line(tooltip, Component.translatable("cwi.furnace.temperature",(int)temperature));
        MachineGoggles.line(tooltip, Component.translatable("cwi.furnace.armor",armor,parallel));
        MachineGoggles.progress(tooltip, progress, duration);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.write(tag,registries,client); tag.putDouble("Temperature",temperature); tag.putBoolean("Formed",formed); tag.putInt("Armor",armor); tag.putInt("Parallel",parallel); tag.putInt("Height",height);
        if (recipeId != null) tag.putString("Recipe",recipeId.toString());
        if (operation != null) tag.put("Operation", operation.save());
        tag.putString("Fingerprint",fingerprint); tag.putInt("Progress",progress); tag.putInt("Duration",duration); tag.putInt("Batch",batch); tag.putBoolean("Working",working);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.read(tag,registries,client); temperature = FurnaceHeat.sanitize(tag.getDouble("Temperature")); formed = client && tag.getBoolean("Formed");
        armor = Math.max(0,tag.getInt("Armor")); parallel = Math.clamp(tag.getInt("Parallel"),0,16); height = Math.clamp(tag.getInt("Height"),0,8);
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null; fingerprint = tag.getString("Fingerprint");
        duration = Math.max(1,tag.getInt("Duration")); progress = Math.clamp(tag.getInt("Progress"),0,duration); batch = Math.clamp(tag.getInt("Batch"),1,16);
        operation = recipeId == null ? null : tag.contains("Operation") ? VesselOperation.load(tag.getCompound("Operation")) : null;
        if (recipeId != null && operation == null) {
            // Legacy ordinary jobs have not paid their inputs. Periodic legacy jobs cannot safely infer paid IO.
            var old = client || level == null ? null : level.getRecipeManager().byKey(recipeId).map(RecipeHolder::value).orElse(null);
            if (old instanceof MachineRecipe recipe && !recipe.periodic()) {
                var state = new CompoundTag(); state.putLong("Seed", level.random.nextLong());
                state.putDouble("Completed", progress / (double)duration); state.putDouble("Pending", -1); state.putInt("Ticks", progress);
                operation = VesselOperation.load(state);
            } else { recipeId = null; progress = batch = 0; }
        }
        working = client && tag.getBoolean("Working"); lastScan = Long.MIN_VALUE;
    }

    private class FluidView implements MutableRecipeFluids {
        private final int start,count; private final boolean insert,extract;
        FluidView(int start,int count,boolean insert,boolean extract) { this.start=start; this.count=count; this.insert=insert; this.extract=extract; }
        public int getTanks() { return count; }
        public FluidStack getFluidInTank(int tank) { return tanks.getFluidInTank(start+tank); }
        public int getTankCapacity(int tank) { return tanks.getTankCapacity(start+tank); }
        public boolean isFluidValid(int tank,FluidStack stack) { return insert && tanks.isFluidValid(start+tank,stack); }
        public void setFluid(int tank,FluidStack stack) { tanks.setFluid(start+tank,stack); }
        public boolean canExtractTank(int tank) { return extract; }
        public boolean canInsertTank(int tank,FluidStack stack) { return insert && tanks.isFluidValid(start+tank,stack); }
        public int fill(FluidStack stack,FluidAction action) {
            if (!insert || stack.isEmpty()) return 0;
            int remaining=stack.getAmount();
            for (boolean empty : new boolean[]{false,true}) for(int i=0;i<count && remaining>0;i++) {
                if (tanks.tank(start+i).isEmpty()!=empty) continue;
                remaining -= tanks.tank(start+i).fill(stack.copyWithAmount(remaining),action);
            }
            return stack.getAmount()-remaining;
        }
        public FluidStack drain(FluidStack stack,FluidAction action) {
            if (!extract || stack.isEmpty()) return FluidStack.EMPTY;
            int remaining=stack.getAmount();
            for(int i=0;i<count && remaining>0;i++) remaining-=tanks.tank(start+i).drain(stack.copyWithAmount(remaining),action).getAmount();
            return stack.copyWithAmount(stack.getAmount()-remaining);
        }
        public FluidStack drain(int amount,FluidAction action) {
            if (!extract || amount<=0) return FluidStack.EMPTY;
            for(int i=0;i<count;i++) if(!getFluidInTank(i).isEmpty()) return drain(getFluidInTank(i).copyWithAmount(amount),action);
            return FluidStack.EMPTY;
        }
    }
}
