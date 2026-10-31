package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.*;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class MachineProcessingGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void testScriptMixerAndIncubatorProcess(GameTestHelper helper) {
        var mixerPos = new BlockPos(1, 1, 1); helper.setBlock(mixerPos, MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity mixer = helper.getBlockEntity(mixerPos); drive(helper, mixerPos, 256);
        mixer.items().setStackInSlot(0, new ItemStack(Items.PRISMARINE_SHARD, 2));
        mixer.fluids().fill(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 21; i++) mixer.tick();
        helper.assertTrue(!mixer.getItem(0).is(Items.PRISMARINE_SHARD) && mixer.getFluid(0).isEmpty(), "Test.js mixer consumes the two-copy batch");
        int count = 0; for (int i = 0; i < mixer.items().getSlots(); i++) if (mixer.getItem(i).is(Items.PRISMARINE_CRYSTALS)) count += mixer.getItem(i).getCount();
        helper.assertTrue(count == 4, "Test.js mixer produces four counted crystals");
        var incubatorPos = new BlockPos(4, 1, 1); helper.setBlock(incubatorPos, MachineBlocks.INCUBATOR.get());
        IncubatorBlockEntity incubator = helper.getBlockEntity(incubatorPos); drive(helper, incubatorPos, 64);
        incubator.items().setStackInSlot(0, new ItemStack(Items.DRAGON_BREATH));
        for (int i = 0; i < 21; i++) incubator.tick();
        helper.assertTrue(incubator.getItem(0).isEmpty() && incubator.getItem(8).is(Items.SLIME_BALL), "Test.js incubator consumes and produces in its native lane");
        helper.succeed();
    }
    private static CreativeMotorBlockEntity drive(GameTestHelper helper, BlockPos machinePos, int rpm) {
        helper.setBlock(machinePos.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(machinePos.below());
        motor.generatedSpeed.setValue(rpm);
        motor.initialize();
        KineticBlockEntity machine = helper.getBlockEntity(machinePos);
        machine.attachKinetics();
        helper.assertTrue(Math.abs(machine.getSpeed()) == Math.abs(rpm) && !machine.isOverStressed(),
                "creative motor drives the real kinetic network at " + rpm + " RPM; actual=" + machine.getSpeed());
        var network = machine.getOrCreateNetwork();
        if (rpm == 0) {
            helper.assertTrue(network == null,
                    "a stopped creative motor leaves the machine without a kinetic network");
        } else {
            helper.assertTrue(network != null && network.calculateStress() == machine.calculateStressApplied() * Math.abs(rpm),
                    "native network tracks the machine's perRPM stress");
        }
        return motor;
    }

    @GameTest(template = "empty",templateNamespace = CwiCore.ID)
    public static void incubatorOwnsProgressAndReloads(GameTestHelper helper) {
        BlockPos pos=new BlockPos(1,1,1);
        helper.setBlock(pos,MachineBlocks.INCUBATOR.get());
        IncubatorBlockEntity machine=helper.getBlockEntity(pos);
        CreativeMotorBlockEntity motor=drive(helper,pos,64);
        machine.items().setStackInSlot(0,new ItemStack(Items.PAPER));
        for(int tick=0;tick<15;tick++) machine.tick();
        helper.assertTrue(machine.progress(0)==15,"first plate progresses at64RPM");
        helper.assertTrue(machine.items().getStackInSlot(0).is(Items.PAPER),"input remains until completion");
        var saved=machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        machine.setRemoved();
        IncubatorBlockEntity restored=new IncubatorBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadCustomOnly(saved,helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(restored);
        restored.initialize();
        restored.attachKinetics();
        machine=restored;
        helper.assertTrue(machine.progress(0)==15,"reload retains plate-bound progress");
        helper.assertTrue(machine.hasSource() && machine.source.equals(motor.getBlockPos()), "reload retains actual kinetic source; saved=" + saved.get("Source")
                + ", source=" + machine.source + ", expected=" + motor.getBlockPos());
        machine.items().setStackInSlot(1,new ItemStack(Items.PAPER));
        machine.tick();
        helper.assertTrue(machine.progress(0)==16 && machine.progress(1)==1,"late arrival cannot inherit older plate clock; first=" + machine.progress(0)
                + ", second=" + machine.progress(1) + ", rpm=" + machine.getSpeed() + ", overstress=" + machine.isOverStressed());
        machine.items().setStackInSlot(0,ItemStack.EMPTY);
        machine.items().setStackInSlot(0,new ItemStack(Items.PAPER));
        helper.assertTrue(machine.progress(0)==0,"reinserted identical plate starts fresh");
        motor.generatedSpeed.setValue(0); machine.tick();
        helper.assertTrue(machine.progress(1)==0,"stopped incubation resets progress");
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void incubatorRunsEightLanes(GameTestHelper helper) {
        BlockPos pos=new BlockPos(1,1,1); helper.setBlock(pos,MachineBlocks.INCUBATOR.get());
        IncubatorBlockEntity machine=helper.getBlockEntity(pos);
        drive(helper,pos,64);
        for(int lane=0;lane<8;lane++)machine.items().setStackInSlot(lane,new ItemStack(Items.PAPER));
        for(int tick=0;tick<40;tick++)machine.tick();
        for(int lane=0;lane<8;lane++){
            helper.assertTrue(machine.getItem(lane).isEmpty(),"each completed plate consumed exactly once");
            helper.assertTrue(machine.getItem(lane+8).is(Items.MAP),"each lane produces its result");
        }
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void mixerBatchesAndSamplesSpeed(GameTestHelper helper) {
        BlockPos pos=new BlockPos(1,1,1);helper.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity machine=helper.getBlockEntity(pos);
        CreativeMotorBlockEntity motor=drive(helper,pos,0);
        machine.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,8));
        machine.fluids().fill(new FluidStack(Fluids.WATER,400),IFluidHandler.FluidAction.EXECUTE);
        machine.tick();
        helper.assertTrue(machine.progress()==0,"zeroRPM cannot select a recipe");
        motor.generatedSpeed.setValue(256);
        for(int tick=0;tick<10;tick++)machine.tick();
        helper.assertTrue(machine.progress()==10 && machine.batch()==4 && machine.duration()==20,"batch4 samples256RPM duration");
        helper.assertTrue(machine.getItem(0).getCount()==8 && machine.getFluid(0).getAmount()==400,"ordinary inputs deferred");
        var saved=machine.saveWithoutMetadata(helper.getLevel().registryAccess());machine.loadCustomOnly(saved,helper.getLevel().registryAccess());
        helper.assertTrue(machine.progress()==10 && machine.batch()==4,"active batch persists across reload");
        motor.generatedSpeed.setValue(-128);
        for(int tick=0;tick<10;tick++)machine.tick();
        int gold=0;for(int slot=0;slot<6;slot++)if(machine.getItem(slot).is(Items.GOLD_INGOT))gold+=machine.getItem(slot).getCount();
        helper.assertTrue(gold==4 && machine.getFluid(0).isEmpty(),"reverse direction completes sampled batch exactly once");
        helper.succeed();
    }
    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void removedInputsAndBlockedOutputsReset(GameTestHelper helper) {
        BlockPos pos=new BlockPos(1,1,1);helper.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity machine=helper.getBlockEntity(pos);
        drive(helper,pos,128);
        machine.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,2));
        machine.fluids().fill(new FluidStack(Fluids.WATER,100),IFluidHandler.FluidAction.EXECUTE);
        machine.tick();
        helper.assertTrue(machine.progress()==1,"valid recipe starts");
        machine.items().setStackInSlot(0,ItemStack.EMPTY);machine.tick();
        helper.assertTrue(machine.progress()==0 && machine.batch()==0,"missing deferred input resets progress");
        helper.assertTrue(machine.getFluid(0).getAmount()==100,"interruption leaves ordinary fluid unconsumed");
        machine.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,2));machine.tick();
        helper.assertTrue(machine.progress()==1,"reinserted input selects a fresh batch");
        machine.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,3));
        for(int slot=1;slot<6;slot++)machine.items().setStackInSlot(slot,new ItemStack(Items.DIAMOND,64));
        machine.tick();
        helper.assertTrue(machine.progress()==0 && machine.batch()==0,"blocking every ordinary output slot resets progress");
        helper.assertTrue(machine.getItem(0).getCount()==3 && machine.getFluid(0).getAmount()==100,"blocked output preserves deferred inputs");
        helper.succeed();
    }

    @GameTest(template="empty",templateNamespace=CwiCore.ID)
    public static void incubatorUsesOwnVisibleOutputFallback(GameTestHelper helper) {
        BlockPos pos=new BlockPos(1,1,1);helper.setBlock(pos,MachineBlocks.INCUBATOR.get());
        IncubatorBlockEntity machine=helper.getBlockEntity(pos);drive(helper,pos,64);
        for(int slot=8;slot<16;slot++)machine.items().setStackInSlot(slot,new ItemStack(Items.DIAMOND));
        machine.items().setStackInSlot(0,new ItemStack(Items.PAPER));
        for(int tick=0;tick<40;tick++)machine.tick();
        helper.assertTrue(machine.getItem(0).is(Items.MAP),"full dedicated outputs fall back to the completed plate's own visible BOTH slot");
        for(int slot=1;slot<8;slot++)helper.assertTrue(machine.getItem(slot).isEmpty(),"a lane never writes to another visible lane");
        for(int slot=8;slot<16;slot++)helper.assertTrue(machine.getItem(slot).is(Items.DIAMOND),"existing dedicated outputs stay unchanged");
        helper.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void overstressedNetworkStopsMixer(GameTestHelper helper) {
        BlockPos enginePos=new BlockPos(4,2,4),mixerPos=enginePos.north().above();
        helper.setBlock(enginePos.below(),Blocks.MAGMA_BLOCK);
        helper.setBlock(enginePos,MachineBlocks.STIRLING_ENGINE.get());
        StirlingEngineBlockEntity engine=helper.getBlockEntity(enginePos);engine.initialize();
        helper.setBlock(enginePos.north(),AllBlocks.GEARBOX.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));
        helper.setBlock(mixerPos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity mixer=helper.getBlockEntity(mixerPos);
        helper.runAfterDelay(5,()->{
            helper.assertTrue(Math.abs(mixer.getSpeed())==32 && !mixer.isOverStressed(),"passive Stirling drives mixer through a native gearbox");
            helper.assertTrue(engine.getOrCreateNetwork().calculateCapacity()==1024,"32RPM Stirling supplies1024SU");
            mixer.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,2));
            mixer.fluids().fill(new FluidStack(Fluids.WATER,100),IFluidHandler.FluidAction.EXECUTE);mixer.tick();
            helper.assertTrue(mixer.progress()>0,"powered real-network mixer starts");
            helper.setBlock(mixerPos.above(),MachineBlocks.LAVA_EXTRACTOR_PUMP.get());
            helper.runAfterDelay(5,()->{
                helper.assertTrue(mixer.isOverStressed() && mixer.getSpeed()==0 && mixer.getTheoreticalSpeed()!=0,"extra pump load overstresses the finite Stirling network");
                helper.assertTrue(mixer.progress()==0,"overstress clears mixerprogress and stops recipes");
                helper.assertTrue(mixer.getItem(0).getCount()==2 && mixer.getFluid(0).getAmount()==100,"overstress preserves ordinary deferred inputs");
                helper.succeed();
            });
        });
    }
}
