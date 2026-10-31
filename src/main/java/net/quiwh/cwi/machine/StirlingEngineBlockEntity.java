package net.quiwh.cwi.machine;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class StirlingEngineBlockEntity extends GeneratingKineticBlockEntity {
    private static final TagKey<Block> PASSIVE_HEATERS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create", "passive_boiler_heaters"));
    private float generatedRpm;
    private Direction lastFacing;

    public StirlingEngineBlockEntity(BlockPos pos, BlockState state) { super(MachineBlocks.STIRLING_ENGINE_ENTITY.get(), pos, state); }
    @Override public float getGeneratedSpeed() { return convertToDirection(generatedRpm, getBlockState().getValue(MachineBlock.FACING)); }

    public static int heatRpm(BlockState heater) {
        if (heater.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            return switch (heater.getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
                case KINDLED -> 64;
                case SEETHING -> 128;
                default -> 0;
            };
        }
        if (heater.hasProperty(CampfireBlock.LIT) && !heater.getValue(CampfireBlock.LIT)) return 0;
        return heater.is(PASSIVE_HEATERS) ? 32 : 0;
    }

    @Override public void initialize() { super.initialize(); refreshHeat(); }
    private void refreshHeat() {
        if (level == null || level.isClientSide || !level.hasChunkAt(worldPosition.below())) return;
        int next = heatRpm(level.getBlockState(worldPosition.below()));
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        if (generatedRpm != next || facing != lastFacing) {
            if (generatedRpm != next) level.playSound(null,worldPosition,
                    next>generatedRpm?MachineEffects.OXYGEN_INTAKE.get():MachineEffects.OXYGEN_OUTTAKE.get(),
                    SoundSource.BLOCKS,.5f,1);
            generatedRpm = next;
            lastFacing = facing;
            updateGeneratedRotation();
            setChanged(); sendData();
        }
        boolean working = next > 0;
        if (getBlockState().getValue(MachineBlock.WORKING) != working)
            level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.WORKING, working), 3);
    }
    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        // Read both heat and orientation from current state, including after loading or wrench rotation.
        refreshHeat();
        if (generatedRpm > 0 && level.getGameTime() % 20 == 0 && level instanceof ServerLevel server) {
            var facing = getBlockState().getValue(MachineBlock.FACING);
            level.playSound(null,worldPosition,AllSoundEvents.STEAM.getMainEvent(),SoundSource.BLOCKS,.4f,1);
            for (var jet : MachineEffects.stirlingJets(worldPosition,facing,level.getGameTime()%40!=0))
                MachineEffects.directed(server,MachineEffects.STEAM_CLOUD.get(),jet,Vec3.ZERO,0);
        }
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.write(tag, registries, client); tag.putFloat("GeneratedRpm", generatedRpm);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean client) {
        super.read(tag, registries, client); generatedRpm = Math.clamp(tag.getFloat("GeneratedRpm"), 0, 128);
    }
}
