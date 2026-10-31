package net.quiwh.cwi;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.*;
import net.quiwh.cwi.machine.recipe.*;

/** Native regressions for the machine contracts shared with Create. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class MachineCreateParityGameTests {
    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void nativeFunnelFeedsVesselWithoutBasinSpout(GameTestHelper h) {
        BlockPos vesselPos=new BlockPos(4,3,3),funnelPos=vesselPos.north(),basinPos=vesselPos.west().above();
        h.setBlock(vesselPos,MachineBlocks.MIXING_VESSEL.get());
        h.setBlock(funnelPos,AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.NORTH));
        com.simibubi.create.content.logistics.funnel.FunnelBlockEntity funnel=h.getBlockEntity(funnelPos);funnel.initialize();funnel.tick();
        h.setBlock(basinPos,AllBlocks.BASIN.get());
        MixingVesselBlockEntity vessel=h.getBlockEntity(vesselPos);
        h.assertTrue(!com.simibubi.create.content.processing.basin.BasinBlock.canOutputTo(h.getLevel(),h.absolutePos(basinPos),Direction.EAST),
                "neighboring basin cannot send its direct outlet into the sealed vessel");
        h.assertTrue(com.simibubi.create.content.logistics.funnel.FunnelBlock.tryInsert(h.getLevel(),h.absolutePos(funnelPos),new ItemStack(Items.DIAMOND,5),true).isEmpty()
                && vessel.getItem(0).isEmpty(),"native funnel simulation respects vessel storage");
        h.assertTrue(com.simibubi.create.content.logistics.funnel.FunnelBlock.tryInsert(h.getLevel(),h.absolutePos(funnelPos),new ItemStack(Items.DIAMOND,5),false).isEmpty()
                && vessel.getItem(0).getCount()==5,"native funnel inserts through vessel capability without a direct basin hook");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID,timeoutTicks=150)
    public static void nativePipesAndPumpTransferVesselFluids(GameTestHelper h) {
        BlockPos sourcePos=new BlockPos(2,3,3),pumpPos=new BlockPos(4,3,3),targetPos=new BlockPos(6,3,3);
        h.setBlock(sourcePos,MachineBlocks.MIXING_VESSEL.get());h.setBlock(targetPos,MachineBlocks.MIXING_VESSEL.get());
        h.setBlock(pumpPos.west(),AllBlocks.FLUID_PIPE.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST,true).setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true));
        h.setBlock(pumpPos.east(),AllBlocks.FLUID_PIPE.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST,true).setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true));
        h.setBlock(pumpPos,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(pumpPos.below(),AllBlocks.COGWHEEL.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));
        BlockPos motorPos=pumpPos.below().west();
        h.setBlock(motorPos,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
        CreativeMotorBlockEntity motor=h.getBlockEntity(motorPos);motor.generatedSpeed.setValue(128);motor.initialize();
        KineticBlockEntity cog=h.getBlockEntity(pumpPos.below());cog.attachKinetics();
        com.simibubi.create.content.fluids.pump.PumpBlockEntity pump=h.getBlockEntity(pumpPos);pump.attachKinetics();
        h.assertTrue(Math.abs(pump.getSpeed())==128,"real gears drive native fluid pump");
        MixingVesselBlockEntity source=h.getBlockEntity(sourcePos),target=h.getBlockEntity(targetPos);
        source.fluids().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE);
        pump.updatePressureChange();
        h.succeedWhen(() -> {
            h.assertTrue(source.getFluid(0).isEmpty() && target.getFluid(0).getAmount()==1000,
                    "native pump and connected pipes drain and fill sealed vessels; source="+source.getFluid(0).getAmount()+", target="+target.getFluid(0).getAmount());
        });
    }
    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void stressDefaultsAndGeneratorOverrides(GameTestHelper h) {
        h.assertTrue(BlockStressValues.getImpact(MachineBlocks.MIXING_VESSEL.get())==8
                && BlockStressValues.getImpact(MachineBlocks.INCUBATOR.get())==2
                && BlockStressValues.getImpact(MachineBlocks.LAVA_EXTRACTOR_PUMP.get())==32
                && BlockStressValues.getCapacity(MachineBlocks.STIRLING_ENGINE.get())==32,"Create API exposes unchanged machine defaults");
        var active=new java.util.concurrent.atomic.AtomicBoolean(true);
        BlockStressValues.CAPACITIES.registerProvider(block -> block==MachineBlocks.STIRLING_ENGINE.get() && active.get() ? ()->48 : null);
        BlockStressValues.CAPACITIES.invalidate();
        try {
            BlockPos pos=new BlockPos(3,3,3);h.setBlock(pos,MachineBlocks.STIRLING_ENGINE.get());
            StirlingEngineBlockEntity engine=h.getBlockEntity(pos);
            h.assertTrue(engine.calculateAddedStressCapacity()==48,"generator reads public Create capacity override");
        } finally {active.set(false);BlockStressValues.CAPACITIES.invalidate();}
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void chancePreflightDoesNotConsumeRandomOrInputs(GameTestHelper h) {
        var inventory=new net.neoforged.neoforge.items.ItemStackHandler(2);
        inventory.setStackInSlot(0,new ItemStack(Items.STONE,64));inventory.setStackInSlot(1,new ItemStack(Items.DIAMOND,64));
        var recipe=new MachineRecipe(MachineRecipes.MIXING,List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.STONE),1,1,false)),List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.CLAY_BALL,64),.5f,false)),List.of(),20,0,0,HeatCondition.NONE);
        var random=net.minecraft.util.RandomSource.create(1234);var control=net.minecraft.util.RandomSource.create(1234);
        h.assertTrue(!RecipeTransaction.complete(recipe,inventory,null,1,random),"full output fails worst-case capacity preflight");
        h.assertTrue(inventory.getStackInSlot(0).getCount()==64 && inventory.getStackInSlot(1).getCount()==64,"blocked chance recipe is atomic");
        h.assertTrue(random.nextFloat()==control.nextFloat(),"failed preflight never rolls chance");
        for(int seed=0;seed<10;seed++) {
            var items=new net.neoforged.neoforge.items.ItemStackHandler(6);items.setStackInSlot(0,new ItemStack(Items.STONE,3));
            random=net.minecraft.util.RandomSource.create(seed);control=net.minecraft.util.RandomSource.create(seed);
            h.assertTrue(RecipeTransaction.complete(recipe,items,null,3,random),"three-copy chance batch commits");
            int expected=0,actual=0;
            var output=new com.simibubi.create.content.processing.recipe.ProcessingOutput(new ItemStack(Items.CLAY_BALL,64),.5f);
            for(int i=0;i<3;i++)expected+=output.rollOutput(control).getCount();
            for(int i=0;i<6;i++)if(items.getStackInSlot(i).is(Items.CLAY_BALL))actual+=items.getStackInSlot(i).getCount();
            h.assertTrue(actual==expected,"batched per-item chance matches native Create across seeds; seed="+seed);
        }
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void manualAccessHonorsMachineSides(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos=new BlockPos(3,3,3);h.setBlock(pos,MachineBlocks.SOLAR_EVAPORATOR.get());
        var bucket=new ItemStack(Items.WATER_BUCKET);player.setItemInHand(InteractionHand.MAIN_HAND,bucket);
        var result=use(h,pos,Direction.UP,player,bucket);
        SolarEvaporatorBlockEntity tray=h.getBlockEntity(pos);
        h.assertTrue(result.consumesAction() && tray.getFluid(0).getAmount()==1000,"open tray supports manual buckets");
        tray.items().setStackInSlot(0,new ItemStack(Items.DIAMOND));player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        use(h,pos,Direction.UP,player,ItemStack.EMPTY);
        h.assertTrue(tray.getItem(0).isEmpty(),"open tray supports empty-hand extraction");
        BlockPos furnacePos=new BlockPos(6,3,3);h.setBlock(furnacePos,MachineBlocks.BLAST_FURNACE.get());
        BlastFurnaceBlockEntity furnace=h.getBlockEntity(furnacePos);
        furnace.fluids().setFluid(4,new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000));
        for(Direction side:List.of(Direction.UP,Direction.SOUTH)) {
            var empty=new ItemStack(Items.BUCKET);player.setItemInHand(InteractionHand.MAIN_HAND,empty);use(h,furnacePos,side,player,empty);
            h.assertTrue(furnace.getFluid(4).getAmount()==1000,"manual access cannot bypass furnace output face");
        }
        var empty=new ItemStack(Items.BUCKET);player.setItemInHand(InteractionHand.MAIN_HAND,empty);use(h,furnacePos,Direction.NORTH,player,empty);
        h.assertTrue(furnace.getFluid(4).isEmpty(),"output face supports manual fluid extraction");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));use(h,furnacePos,Direction.NORTH,player,player.getMainHandItem());
        h.assertTrue(furnace.getFluid(4).isEmpty(),"furnace output face never accepts manual fluid insertion");
        h.succeed();
    }

    private static net.minecraft.world.ItemInteractionResult use(GameTestHelper h,BlockPos pos,Direction side,
            net.minecraft.world.entity.player.Player player,ItemStack stack) {
        return h.getBlockState(pos).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(h.absolutePos(pos)),side,h.absolutePos(pos),false));
    }
    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void comparatorTracksOnlyVesselInputs(GameTestHelper h) {
        BlockPos pos = new BlockPos(3,3,3), comparatorPos = pos.south();
        h.setBlock(pos, MachineBlocks.MIXING_VESSEL.get());
        h.setBlock(comparatorPos.below(), Blocks.STONE);
        h.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.NORTH));
        MixingVesselBlockEntity vessel = h.getBlockEntity(pos);
        h.assertTrue(h.getBlockState(pos).getAnalogOutputSignal(h.getLevel(), h.absolutePos(pos)) == 0, "empty vessel signal0");
        vessel.items().setStackInSlot(4, new ItemStack(Items.DIAMOND,64));
        vessel.fluids().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000), IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(vessel.comparatorSignal()==0,"outputs and fluids do not change the input-inventory comparator");
        for (int slot=0;slot<4;slot++) vessel.items().setStackInSlot(slot,new ItemStack(Items.NETHERITE_INGOT,64));
        h.assertTrue(vessel.comparatorSignal()==15,"four full input stacks give signal15");
        h.runAfterDelay(4, () -> {
            net.minecraft.world.level.block.entity.ComparatorBlockEntity comparator = h.getBlockEntity(comparatorPos);
            h.assertTrue(comparator.getOutputSignal()==15,"real comparator updates after automated insertion");
            for (int slot=0;slot<4;slot++) vessel.getItemCapability(Direction.UP).extractItem(slot,64,false);
            h.runAfterDelay(4, () -> {
                h.assertTrue(comparator.getOutputSignal()==0,"real comparator updates after automated extraction");
                h.succeed();
            });
        });
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void vesselPreservesAllSidedAutomation(GameTestHelper h) {
        BlockPos pos = new BlockPos(3,3,3); h.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity vessel=h.getBlockEntity(pos);
        for(Direction side:Direction.values()) {
            var items=h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,h.absolutePos(pos),side);
            var fluids=h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,h.absolutePos(pos),side);
            h.assertTrue(items!=null && fluids!=null,"every side exposes standard NeoForge handlers: "+side);
            h.assertTrue(items.insertItem(0,new ItemStack(Items.DIAMOND,2),true).isEmpty() && vessel.getItem(0).isEmpty(),"simulated insertion has no side effects");
            h.assertTrue(items.insertItem(4,new ItemStack(Items.DIAMOND),false).getCount()==1,"automation cannot insert into output-only slots");
            items.insertItem(0,new ItemStack(Items.DIAMOND,2),false);
            h.assertTrue(items.extractItem(0,2,false).getCount()==2,"external mods can insert/extract through standard handlers");
            var water=new FluidStack(net.minecraft.world.level.material.Fluids.WATER,250);
            h.assertTrue(fluids.fill(water,IFluidHandler.FluidAction.SIMULATE)==250 && vessel.getFluid(0).isEmpty(),"simulated fluid fill has no effects");
            h.assertTrue(fluids.fill(water,IFluidHandler.FluidAction.EXECUTE)==250 && fluids.drain(water,IFluidHandler.FluidAction.EXECUTE).getAmount()==250,"pipe-compatible fill and drain on every side");
        }
        // Exercise the native hopper transfer path, rather than only calling its target capability.
        h.setBlock(pos.above(),Blocks.HOPPER);
        net.minecraft.world.level.block.entity.HopperBlockEntity hopper=h.getBlockEntity(pos.above());
        hopper.setItem(0,new ItemStack(Items.NETHERITE_SCRAP));
        h.runAfterDelay(12, () -> {
            h.assertTrue(vessel.getItem(0).is(Items.NETHERITE_SCRAP),"native hopper inserts into sealed vessel");
            h.succeed();
        });
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void recipePriorityAndNumericSpeedFeedback(GameTestHelper h) {
        BlockPos pos=new BlockPos(3,3,3);h.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity vessel=h.getBlockEntity(pos);drive(h,pos,256);
        vessel.items().setStackInSlot(0,new ItemStack(Items.NETHER_STAR,2));
        for(int i=0;i<4;i++)vessel.tick();
        h.assertTrue(vessel.items().getStackInSlot(0).is(Items.DIAMOND),"two-ingredient recipe beats lexicographically earlier simple recipe");
        vessel.items().setStackInSlot(0,new ItemStack(Items.ECHO_SHARD,2));
        drive(h,pos,64);vessel.tick();
        h.assertTrue(vessel.minimumRequiredRpm()==128 && !vessel.isSpeedRequirementFulfilled(),"vessel exposes blocked recipe's numeric128RPM threshold");
        var tag=vessel.getUpdateTag(h.getLevel().registryAccess());
        var clientCopy=new MixingVesselBlockEntity(vessel.getBlockPos(),vessel.getBlockState());
        clientCopy.handleUpdateTag(tag,h.getLevel().registryAccess());
        h.assertTrue(clientCopy.minimumRequiredRpm()==128,"client packet retains actual recipe speed for goggles");
        BlockPos incubatorPos=new BlockPos(6,3,3);h.setBlock(incubatorPos,MachineBlocks.INCUBATOR.get());
        IncubatorBlockEntity incubator=h.getBlockEntity(incubatorPos);drive(h,incubatorPos,64);
        incubator.items().setStackInSlot(0,new ItemStack(Items.PHANTOM_MEMBRANE));incubator.tick();
        h.assertTrue(incubator.minimumRequiredRpm()==128 && !incubator.isSpeedRequirementFulfilled(),"incubator exposes recipe requirement above its64RPM base");
        incubator.items().setStackInSlot(1,new ItemStack(Items.MAP));incubator.tick();
        h.assertTrue(incubator.minimumRequiredRpm()==128,"a plate without a matching recipe cannot hide another lane's128RPM requirement");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void commonGogglesReportContentsAndStatus(GameTestHelper h) {
        var disableStress=AllConfigs.server().kinetics.disableStress;boolean previous=disableStress.get();
        // Native stress formatting accesses client I18n; test Core's common contents with stress disabled.
        disableStress.set(true);
        try {
            int x=1;
            for(var block:List.of(MachineBlocks.INCUBATOR.get(),MachineBlocks.MIXING_VESSEL.get(),
                    MachineBlocks.BLAST_FURNACE.get(),MachineBlocks.SOLAR_EVAPORATOR.get(),MachineBlocks.LAVA_EXTRACTOR_HEAD.get())) {
                BlockPos pos=new BlockPos(x++,3,3);h.setBlock(pos,block);MachineBlockEntity machine=h.getBlockEntity(pos);
                if(machine.items().getSlots()>0)machine.items().setStackInSlot(0,new ItemStack(machine instanceof IncubatorBlockEntity ? Items.PAPER : Items.DIAMOND));
                if(machine.fluids().getTanks()>0)machine.fluids().setFluid(0,new FluidStack(net.minecraft.world.level.material.Fluids.LAVA,123));
                var tooltip=new ArrayList<Component>();
                h.assertTrue(machine.addToGoggleTooltip(tooltip,false),"machine supplies goggles information");
                String text=tooltip.stream().map(Component::getString).collect(java.util.stream.Collectors.joining("\n"));
                h.assertTrue(tooltip.stream().anyMatch(c -> hasTranslation(c, "cwi.machine.contents")),"goggles includes contents section: "+block);
                h.assertTrue(machine.fluids().getTanks()==0 || text.contains("123"),"goggles includes exactmB amount: "+text);
                h.assertTrue(!(machine instanceof BlastFurnaceBlockEntity) || tooltip.stream().anyMatch(c -> hasTranslation(c, "cwi.furnace.temperature")),"furnace keeps temperature feedback");
            }
        } finally {disableStress.set(previous);}
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void lazySyncRetainsImmediateSaves(GameTestHelper h) {
        BlockPos pos=new BlockPos(3,3,3);h.setBlock(pos,MachineBlocks.SOLAR_EVAPORATOR.get());
        var machine=new CountingMachine(h.absolutePos(pos),h.getBlockState(pos));machine.setLevel(h.getLevel());
        machine.items().setStackInSlot(0,new ItemStack(Items.DIAMOND));
        machine.items().setStackInSlot(1,new ItemStack(Items.EMERALD));
        machine.fluids().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,500),IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(machine.packets==0,"multiple mutations queue one ordinary update");
        var saved=machine.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored=new SolarEvaporatorBlockEntity(machine.getBlockPos(),machine.getBlockState());restored.loadCustomOnly(saved,h.getLevel().registryAccess());
        h.assertTrue(restored.getItem(0).is(Items.DIAMOND) && restored.getFluid(0).getAmount()==500,"storage saves before a queued packet is sent");
        machine.tick();h.assertTrue(machine.packets==1,"first tick flushes queued storage");
        machine.items().setStackInSlot(2,new ItemStack(Items.COAL));
        for(int i=0;i<7;i++)machine.tick();
        h.assertTrue(machine.packets==1,"eight-tick cooldown batches ordinary changes");
        machine.tick();h.assertTrue(machine.packets==2,"cooldown flushes final state");
        machine.items().setStackInSlot(2,ItemStack.EMPTY);machine.sendData();
        h.assertTrue(machine.packets==3,"critical changes retain immediate update path");
        for(int i=0;i<8;i++)machine.tick();
        h.assertTrue(machine.packets==3,"immediate update clears duplicate pending snapshot");
        h.succeed();
    }

    private static boolean hasTranslation(Component component, String key) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translation
                && translation.getKey().equals(key)) return true;
        return component.getSiblings().stream().anyMatch(child -> hasTranslation(child, key));
    }

    private static final class CountingMachine extends MachineBlockEntity {
        int packets;
        CountingMachine(BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {super(MachineBlocks.SOLAR_EVAPORATOR_ENTITY.get(),pos,state,3,3000);}
        @Override public void sendData(){packets++;super.sendData();}
    }

    private static void drive(GameTestHelper h,BlockPos pos,int rpm) {
        h.setBlock(pos.below(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,Direction.UP));
        CreativeMotorBlockEntity motor=h.getBlockEntity(pos.below());motor.generatedSpeed.setValue(rpm);motor.initialize();
        KineticBlockEntity machine=h.getBlockEntity(pos);machine.attachKinetics();
        h.assertTrue(Math.abs(machine.getSpeed())==rpm,"real kinetic network supplies requested speed");
    }
    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void configuredHighSpeedSurvives(GameTestHelper h) {
        var config = AllConfigs.server().kinetics.maxRotationSpeed;
        int previous = config.get();
        BlockPos corePos = new BlockPos(4,4,4), nativePos = new BlockPos(4,2,4);
        BlockPos largePos=new BlockPos(3,3,3),smallPos=new BlockPos(4,3,4),motorPos=largePos.below();
        config.set(512);
        try {
            h.setBlock(corePos, MachineBlocks.MIXING_VESSEL.get());
            h.setBlock(nativePos, AllBlocks.SHAFT.getDefaultState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Y));
            h.setBlock(largePos,AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y));
            h.setBlock(smallPos,AllBlocks.COGWHEEL.getDefaultState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y));
            h.setBlock(motorPos,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,Direction.UP));
            CreativeMotorBlockEntity motor=h.getBlockEntity(motorPos);motor.generatedSpeed.setValue(256);motor.initialize();
            for(BlockPos p:List.of(largePos,smallPos)) { KineticBlockEntity cog=h.getBlockEntity(p);cog.attachKinetics(); }
            for (BlockPos p : List.of(corePos,nativePos)) {
                KineticBlockEntity target=h.getBlockEntity(p); target.attachKinetics();
                h.assertTrue(Math.abs(target.getSpeed())==512,"actual Create gears transmit configured512RPM; actual="+target.getSpeed());
            }
            KineticBlockEntity nativeShaft=h.getBlockEntity(nativePos); nativeShaft.tick();
            MixingVesselBlockEntity core=h.getBlockEntity(corePos); core.tick();
            h.assertTrue(h.getBlockState(nativePos).is(AllBlocks.SHAFT.get()),"Create shaft survives allowed512RPM");
            h.assertTrue(h.getBlockState(corePos).is(MachineBlocks.MIXING_VESSEL.get()),"Core survives configured512RPM allowance");
            h.assertTrue(MachineKinetics.mixerDuration(100,512)==50,"processing uses the256RPM timing reference within the configured512RPM limit");
        } finally {
            for(BlockPos p:List.of(corePos,nativePos,largePos,smallPos,motorPos)) h.setBlock(p,Blocks.AIR);
            config.set(previous);
        }
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void createStressRegistryControlsMachine(GameTestHelper h) {
        var core=MachineBlocks.MIXING_VESSEL.get(); var nativeBlock=AllBlocks.MECHANICAL_MIXER.get();
        var active=new java.util.concurrent.atomic.AtomicBoolean(true);
        BlockStressValues.IMPACTS.registerProvider(block->block==core && active.get()?()->12:null);
        BlockStressValues.IMPACTS.invalidate();
        try {
            h.setBlock(new BlockPos(3,3,3),core); h.setBlock(new BlockPos(6,3,3),nativeBlock);
            KineticBlockEntity c=h.getBlockEntity(new BlockPos(3,3,3)), n=h.getBlockEntity(new BlockPos(6,3,3));
            h.assertTrue(BlockStressValues.getImpact(core)==12 && c.calculateStressApplied()==12,"Core honors Create's registered12SU");
            h.assertTrue(n.calculateStressApplied()==BlockStressValues.getImpact(nativeBlock) && n.calculateStressApplied()>0,"native mixer honors its registered stress value");
        } finally {
            active.set(false);BlockStressValues.IMPACTS.invalidate();
        }
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void minimumSpeedIsVisibleToCreate(GameTestHelper h) {
        BlockPos cpos=new BlockPos(3,3,3), npos=new BlockPos(6,3,3);
        h.setBlock(cpos,MachineBlocks.INCUBATOR.get()); h.setBlock(npos,AllBlocks.MECHANICAL_MIXER.get());
        IncubatorBlockEntity c=h.getBlockEntity(cpos); KineticBlockEntity n=h.getBlockEntity(npos);
        c.setSpeed(16); n.setSpeed(16);
        h.assertTrue(!c.isSpeedRequirementFulfilled(),"Create detects the incubator's fixed64RPM requirement at16RPM");
        h.assertTrue(!n.isSpeedRequirementFulfilled(),"Create detects its native mixer's insufficient speed");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void hatchPickupWithoutRotation(GameTestHelper h) {
        BlockPos hatchPos=new BlockPos(3,3,3), mixerPos=new BlockPos(6,3,3);
        h.setBlock(hatchPos,MachineBlocks.FURNACE_HATCH.get()); h.setBlock(mixerPos,MachineBlocks.MIXING_VESSEL.get());
        var player=h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
        WrenchItem wrench=AllItems.WRENCH.get();
        for(boolean sneak:new boolean[]{false,true}) {
            player.setShiftKeyDown(sneak);
            var result=wrench.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(h.absolutePos(hatchPos)),Direction.UP,h.absolutePos(hatchPos),false)));
            h.assertTrue(sneak ? result.consumesAction() && h.getBlockState(hatchPos).isAir() : h.getBlockState(hatchPos).is(MachineBlocks.FURNACE_HATCH.get()),"hatch stays fixed on normal wrenching and is picked up on sneak-wrenching");
        }
        player.setShiftKeyDown(false);
        var rotation=wrench.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(h.absolutePos(mixerPos)),Direction.UP,h.absolutePos(mixerPos),false)));
        h.assertTrue(rotation.consumesAction() && h.getBlockState(mixerPos).getValue(MachineBlock.FACING)!=Direction.NORTH,"shared Core kinetic block inherits functional Create wrench rotation");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void sealedVesselRejectsManualTransfers(GameTestHelper h) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos cpos=new BlockPos(3,3,3), npos=new BlockPos(6,3,3);
        h.setBlock(cpos,MachineBlocks.MIXING_VESSEL.get()); h.setBlock(npos,AllBlocks.BASIN.get());
        for(BlockPos pos:List.of(cpos,npos)) {
            var stack=new ItemStack(Items.WATER_BUCKET); player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            h.getBlockState(pos).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(h.absolutePos(pos)),Direction.UP,h.absolutePos(pos),false));
        }
        MixingVesselBlockEntity core=h.getBlockEntity(cpos); BasinBlockEntity basin=h.getBlockEntity(npos);
        h.assertTrue(core.getFluid(0).isEmpty(),"sealed vessel rejects manual buckets");
        h.assertTrue(basin.getTanks().getFirst().getPrimaryHandler().getFluidAmount()==1000,"same bucket interaction fills native Create basin");
        core.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT));basin.getInputInventory().insertItem(0,new ItemStack(Items.IRON_INGOT),false);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        for(BlockPos pos:List.of(cpos,npos)) h.getBlockState(pos).useItemOn(ItemStack.EMPTY,h.getLevel(),player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(h.absolutePos(pos)),Direction.UP,h.absolutePos(pos),false));
        h.assertTrue(core.getItem(0).is(Items.IRON_INGOT) && basin.getInputInventory().getStackInSlot(0).isEmpty(),
                "sealed vessel requires automation while the native open basin accepts empty-hand retrieval");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void countedChanceMatchesCreate(GameTestHelper h) {
        BlockPos pos=new BlockPos(3,3,3);h.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity core=h.getBlockEntity(pos);core.items().setStackInSlot(0,new ItemStack(Items.STONE));
        var recipe=new MachineRecipe(MachineRecipes.MIXING,List.of(new MachineRecipe.ItemInput(Ingredient.of(Items.STONE),1,1,false)),List.of(),
                List.of(new MachineRecipe.ItemOutput(new ItemStack(Items.CLAY_BALL,64),.5f,false)),List.of(),20,0,0,HeatCondition.NONE);
        h.assertTrue(RecipeTransaction.complete(recipe,core.items(),core.fluids(),1,net.minecraft.util.RandomSource.create(1234)),"Core commits chance recipe");
        int count=0;for(int slot=0;slot<6;slot++)if(core.getItem(slot).is(Items.CLAY_BALL))count+=core.getItem(slot).getCount();
        var nativeCount=new com.simibubi.create.content.processing.recipe.ProcessingOutput(new ItemStack(Items.CLAY_BALL,64),.5f)
                .rollOutput(net.minecraft.util.RandomSource.create(1234)).getCount();
        h.assertTrue(count==nativeCount,"Core rolls independently per item, matching Create with the same seed; core="+count+", native="+nativeCount);
        h.assertTrue(nativeCount>0 && nativeCount<64,"Create rolls separately for each item; actual="+nativeCount);
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void sealedVesselComparatorAndGoggles(GameTestHelper h) throws Exception {
        BlockPos cpos=new BlockPos(3,3,3),npos=new BlockPos(6,3,3);
        h.setBlock(cpos,MachineBlocks.MIXING_VESSEL.get());h.setBlock(npos,AllBlocks.BASIN.get());
        MixingVesselBlockEntity core=h.getBlockEntity(cpos);BasinBlockEntity basin=h.getBlockEntity(npos);
        h.assertTrue(BlockEntityBehaviour.get(core,FilteringBehaviour.TYPE)==null && BlockEntityBehaviour.get(basin,FilteringBehaviour.TYPE)!=null,"Core has no output recipe filter; Create basin does");
        h.assertTrue(BlockEntityBehaviour.get(core,DirectBeltInputBehaviour.TYPE)==null && BlockEntityBehaviour.get(basin,DirectBeltInputBehaviour.TYPE)!=null,"Core lacks Create direct belt insertion behavior");
        h.assertTrue(h.getBlockState(cpos).hasAnalogOutputSignal() && h.getBlockState(npos).hasAnalogOutputSignal(),"both vessel and native basin support comparators");
        h.assertTrue(MixingVesselBlockEntity.class.getMethod("addToGoggleTooltip",List.class,boolean.class).getDeclaringClass()!=KineticBlockEntity.class
                && BasinBlockEntity.class.getMethod("addToGoggleTooltip",List.class,boolean.class).getDeclaringClass()==BasinBlockEntity.class,
                "both vessel and native basin add contents goggles");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void machinesHavePickaxeTags(GameTestHelper h) {
        var pickaxe=new ItemStack(Items.DIAMOND_PICKAXE);
        var nativeState=AllBlocks.MECHANICAL_MIXER.getDefaultState();
        h.assertTrue(nativeState.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),"native Create mixer has pickaxe mining tag");
        for(var block:List.of(MachineBlocks.BLAST_FURNACE.get(),MachineBlocks.FURNACE_HATCH.get(),MachineBlocks.INCUBATOR.get(),
                MachineBlocks.MIXING_VESSEL.get(),MachineBlocks.STIRLING_ENGINE.get(),MachineBlocks.SOLAR_EVAPORATOR.get(),
                MachineBlocks.LAVA_EXTRACTOR_HEAD.get(),MachineBlocks.LAVA_EXTRACTOR_PUMP.get(),
                MachineBlocks.LAVA_EXTRACTOR_STRESS_INPUT.get(),MachineBlocks.LAVA_EXTRACTOR_PUMP_BLADE.get())) {
            var state=block.defaultBlockState();
            h.assertTrue(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),"Core machine has pickaxe mining tag: "+block);
            h.assertTrue(pickaxe.getDestroySpeed(state)>1 && pickaxe.getDestroySpeed(nativeState)>1,
                    "diamond pickaxe receives native mining bonus: "+block);
        }
        h.succeed();
    }
}
