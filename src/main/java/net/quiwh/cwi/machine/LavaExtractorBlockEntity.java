package net.quiwh.cwi.machine;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class LavaExtractorBlockEntity extends MachineBlockEntity {
    private int pumpCount;
    private boolean formed;
    private static final ResourceLocation LEGACY_FLOOR = ResourceLocation.fromNamespaceAndPath("kubejs", "eruptive_bedrock");
    private static final TagKey<Block> FLOORS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("cwi", "lava_extractor_floor"));

    public LavaExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(MachineBlocks.LAVA_EXTRACTOR_HEAD_ENTITY.get(), pos, state, 0, 4000);
        tanks.setValidator((tank, fluid) -> fluid.is(Fluids.LAVA));
    }
    public boolean isFormed() { return formed; }
    public int pumpCount() { return pumpCount; }
    @Override protected void addMachineInfo(List<Component> tooltip) {
        MachineGoggles.line(tooltip, Component.translatable(formed ? "cwi.machine.formed" : "cwi.machine.unformed"));
        MachineGoggles.line(tooltip, Component.translatable("cwi.machine.pumps", pumpCount));
        MachineGoggles.line(tooltip, Component.translatable("cwi.machine.minimum_rpm", "128"));
    }
    @Override public @Nullable IFluidHandler getFluidCapability(@Nullable Direction side) {
        return side == null || side.getAxis().isHorizontal() ? tanks : null;
    }
    @Override protected AABB createRenderBoundingBox() { return new AABB(worldPosition).inflate(2); }

    /** Rechecks live blocks without force-loading a chunk or trusting stale formation data. */
    private int findPumps() {
        if (level == null || !level.hasChunkAt(worldPosition.below())) return 0;
        BlockState floor = level.getBlockState(worldPosition.below());
        if (!floor.is(FLOORS) && !BuiltInRegistries.BLOCK.getKey(floor.getBlock()).equals(LEGACY_FLOOR)) return 0;
        int count = 0;
        for (int offset = 1; offset <= 4; offset++) {
            BlockPos pos = worldPosition.above(offset);
            if (!level.hasChunkAt(pos)) return 0;
            BlockState state = level.getBlockState(pos);
            if (state.is(MachineBlocks.LAVA_EXTRACTOR_PUMP.get()) && count < 3) { count++; continue; }
            return count > 0 && state.is(MachineBlocks.LAVA_EXTRACTOR_STRESS_INPUT.get()) ? count : 0;
        }
        return 0;
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        int pumps = findPumps();
        if (formed != (pumps > 0) || pumpCount != pumps) { formed = pumps > 0; pumpCount = pumps; changed(); }
        if (!formed || level.getGameTime() % 40 != 0) return;
        float rpm = 0;
        if (level.getBlockEntity(worldPosition.above()) instanceof KineticBlockEntity first && !first.isOverStressed()) {
            float speed=Math.abs(first.getSpeed());
            if (MachineKinetics.canWork(speed,false,1))
                level.playSound(null,worldPosition,MachineEffects.DRILLING.get(),SoundSource.BLOCKS,speed/512f,.25f);
        }
        for (int offset = 1; offset <= pumps; offset++) {
            if (!(level.getBlockEntity(worldPosition.above(offset)) instanceof KineticBlockEntity pump) || pump.isOverStressed()) return;
            float speed = Math.abs(pump.getSpeed());
            if (!MachineKinetics.canWork(speed, pump.isOverStressed(), 128)) return;
            if (offset == 1) rpm = speed;
        }
        int amount = MachineKinetics.lavaBurst(pumps, rpm, false);
        tanks.fill(new FluidStack(Fluids.LAVA, amount), IFluidHandler.FluidAction.EXECUTE);
        if (level instanceof ServerLevel server) {
            double spread=Math.sqrt(pumps-1)/4;
            double y=worldPosition.getY()+1.2+2*spread;
            server.sendParticles(MachineEffects.BREAKDOWN_SMOKE.get(),worldPosition.getX()+.5,y,worldPosition.getZ()+.5,
                    3+2*pumps,.22,spread,.22,0);
            server.sendParticles(MachineEffects.FLAMETHROWER_BURST.get(),worldPosition.getX()+.5,y,worldPosition.getZ()+.5,
                    3+2*pumps,.22,spread,.22,0);
            level.playSound(null,worldPosition,SoundEvents.BUCKET_EMPTY_LAVA,SoundSource.BLOCKS,rpm/256f,.5f);
        }
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.write(tag, registries, client); tag.putBoolean("Formed", formed); tag.putInt("Pumps", pumpCount);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.read(tag, registries, client); formed = client && tag.getBoolean("Formed"); pumpCount = client ? tag.getInt("Pumps") : 0;
    }
}
