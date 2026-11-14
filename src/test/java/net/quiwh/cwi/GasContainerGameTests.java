package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.MachineBlocks;
import net.quiwh.cwi.machine.SolarEvaporatorBlockEntity;
import net.quiwh.cwi.pollution.GasClouds;
import net.quiwh.cwi.pollution.GasProfiles;
import net.quiwh.cwi.pollution.GasProfile;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class GasContainerGameTests {
    private static FluidStack gas(int amount) {
        return new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("kubejs:ammonia")),amount);
    }
    private static BlockPos position(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(4,3,4));
        h.getLevel().setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
        return pos;
    }
    private static void vent(GameTestHelper h,BlockPos pos,IFluidHandler handler) {
        double before=GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime());
        h.assertTrue(handler.fill(gas(750),FluidAction.SIMULATE)==750,"open container accepts a simulated gas transfer");
        h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==before,
                "simulation cannot create pollution");
        h.assertTrue(handler.fill(gas(750),FluidAction.EXECUTE)==750,"executed transfer reports exactly the released gas");
        h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==before+750,
                "gas immediately enters the atmosphere at the container opening");
        for(int tank=0;tank<handler.getTanks();tank++)
            h.assertTrue(handler.getFluidInTank(tank).isEmpty(),"open container does not retain released gas");
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void basinVentsCommittedGasAndRetainsOrdinaryLiquids(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
        var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,pos,null);
        vent(h,pos,handler);
        var water=new FluidStack(net.minecraft.world.level.material.Fluids.WATER,500);
        h.assertTrue(handler.fill(water,FluidAction.EXECUTE)==500,"ordinary liquid storage is unchanged");
        h.assertTrue(handler.drain(water,FluidAction.SIMULATE).getAmount()==500,"stored water stays extractable");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void itemDrainInternalTransfersVentGas(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.ITEM_DRAIN.getDefaultState());
        var entity=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)h.getLevel().getBlockEntity(pos);
        var tanks=(SmartFluidTankBehaviour)entity.getBehaviour(SmartFluidTankBehaviour.TYPE);
        vent(h,pos,tanks.getPrimaryHandler());
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void solarEvaporatorVentsExternalAndDirectGasWrites(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,MachineBlocks.SOLAR_EVAPORATOR.get().defaultBlockState());
        var entity=(SolarEvaporatorBlockEntity)h.getLevel().getBlockEntity(pos);
        vent(h,pos,entity.fluids());
        entity.fluids().setFluid(0,gas(2000));
        h.assertTrue(entity.getFluid(0).isEmpty(),"direct committed storage writes also release gas");
        h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==2750,
                "direct writes release their full quantity once");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void blockedContainerRefusesIncomingGasWithoutLoss(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
        h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());
        var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,pos,null);
        h.assertTrue(handler.fill(gas(750),FluidAction.SIMULATE)==0,"blocked simulated vent refuses gas");
        h.assertTrue(handler.fill(gas(750),FluidAction.EXECUTE)==0,"blocked executed vent leaves gas upstream");
        for(int tank=0;tank<handler.getTanks();tank++)h.assertTrue(handler.getFluidInTank(tank).isEmpty(),"no hidden gas storage on refusal");
        h.succeed();
    }
    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void committedBasinOutputsReleaseGasInsteadOfFillingTheTank(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
        var basin=(BasinBlockEntity)h.getLevel().getBlockEntity(pos);
        var fluids=java.util.List.of(gas(500));
        h.assertTrue(basin.acceptOutputs(java.util.List.of(),fluids,true),"recipe capacity simulation accepts ventable gas");
        h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==0,
                "recipe capacity probe does not release gas");
        h.assertTrue(basin.acceptOutputs(java.util.List.of(),fluids,false),"actual recipe outputs are accepted");
        h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==500,
                "committed recipe gas is released exactly once");
        h.assertTrue(basin.getTanks().getSecond().isEmpty(),"gas output never remains in the open basin");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void basinGasProductsCannotEscapeThroughItsSpoutIntoSealedStorage(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState()
                .setValue(com.simibubi.create.content.processing.basin.BasinBlock.FACING,Direction.EAST));
        var target=pos.below().east();h.getLevel().setBlockAndUpdate(target,AllBlocks.FLUID_TANK.getDefaultState());
        var basin=(BasinBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertTrue(basin.acceptOutputs(java.util.List.of(),java.util.List.of(gas(500)),false),"gas recipe output can vent with a connected spout");
        h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==500,
                "an open basin vents its own gas output before it can enter the spout buffer");
        for(int i=0;i<10;i++)basin.tick();
        var tank=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,target,null);
        h.assertTrue(tank.getFluidInTank(0).isEmpty(),"spout routing cannot bypass the open-container rule");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void blockedCommittedContentsRetryWithoutPollingEmptyContainers(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
        h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());
        var basin=(BasinBlockEntity)h.getLevel().getBlockEntity(pos);
        var tank=basin.getTanks().getSecond().getPrimaryHandler();tank.setFluid(gas(600));
        h.assertTrue(tank.getFluidAmount()==600,"blocked committed writes keep their gas in real saved storage");
        h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
        var level=h.getLevel();long old=level.getGameTime();
        try {
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime((old/10+1)*10+5);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(()->true,level));
            h.assertTrue(tank.isEmpty(),"a queued blocked release retries when its opening becomes available");
            h.assertTrue(GasClouds.get(level).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),level.getGameTime())==600,"retry emits each saved mB once");
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(()->true,level));
            h.assertTrue(GasClouds.get(level).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),level.getGameTime())==600,"repeating the event cannot duplicate a release");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void sealedCreateTankAndCoreVesselRetainGas(GameTestHelper h) {
        for(var block:java.util.List.of(AllBlocks.FLUID_TANK.getDefaultState(),MachineBlocks.MIXING_VESSEL.get().defaultBlockState())) {
            var pos=position(h);h.getLevel().setBlockAndUpdate(pos,block);
            var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,pos,null);
            h.assertTrue(handler.fill(gas(500),FluidAction.EXECUTE)==500,"sealed equipment accepts gas into storage");
            h.assertTrue(handler.drain(gas(500),FluidAction.SIMULATE).getAmount()==500,"sealed equipment keeps its exact gas amount");
        }
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void nativeItemDrainRecipeReleasesGasExactlyOnce(GameTestHelper h) {
        var manager=h.getLevel().getRecipeManager();var old=manager.getRecipes();
        var recipe=NativeAdvancedTestSupport.recipe(h,"create:emptying","gas_emptying","""
                {"ingredients":[{"item":"minecraft:sand"}],"results":[{"id":"minecraft:clay_ball"},{"id":"kubejs:ammonia","amount":1000}]}
                """);
        try {
            manager.replaceRecipes(java.util.List.of(recipe));var pos=position(h);
            var drain=(com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity)NativeAdvancedTestSupport.place(h,pos.subtract(h.absolutePos(BlockPos.ZERO)),"create:item_drain");
            drain.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE).handleInsertion(
                    new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SAND)),Direction.EAST,false);
            for(int i=0;i<30;i++)drain.tick();
            h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==1000,
                    "actual item-emptying execution emits one fluid result, without emitting during its probes");
            h.assertTrue(((SmartFluidTankBehaviour)drain.getBehaviour(SmartFluidTankBehaviour.TYPE)).isEmpty(),"drain gas result never persists as liquid");
        } finally {manager.replaceRecipes(old);}
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void savedContainerGasReleasesOnceAfterInitialization(GameTestHelper h) {
        for(var state:java.util.List.of(AllBlocks.BASIN.getDefaultState(),AllBlocks.ITEM_DRAIN.getDefaultState(),MachineBlocks.SOLAR_EVAPORATOR.get().defaultBlockState())) {
            var pos=position(h);h.getLevel().setBlockAndUpdate(pos,state);
            h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());
            var owner=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)h.getLevel().getBlockEntity(pos);
            if(owner instanceof SolarEvaporatorBlockEntity solar)solar.fluids().setFluid(0,gas(400));
            else if(owner instanceof BasinBlockEntity basin)basin.getTanks().getFirst().getPrimaryHandler().setFluid(gas(400));
            else ((SmartFluidTankBehaviour)owner.getBehaviour(SmartFluidTankBehaviour.TYPE)).getPrimaryHandler().setFluid(gas(400));
            var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());owner.setRemoved();
            var restored=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)owner.getType().create(pos,state);
            restored.setLevel(h.getLevel());
            h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
            double before=GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime());
            restored.loadCustomOnly(saved,h.getLevel().registryAccess());
            h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==before,"deserialization alone never emits gas");
            h.getLevel().setBlockEntity(restored);restored.initialize();
            net.quiwh.cwi.pollution.GasContainers.releaseStored(restored);
            h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==before+400,"loaded committed contents emit once after initialization");
            var after=restored.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(!after.equals(saved),"released contents are removed from the next saved block entity");
        }
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void gasBatchPreflightIncludesEveryOutputAndRefusedAmountsStaySaved(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
        var basin=(BasinBlockEntity)h.getLevel().getBlockEntity(pos);var level=h.getLevel();var clouds=GasClouds.get(level);
        clouds.emit(level,pos.above(),GasProfiles.byId("kubejs:ammonia"),999_999_250,false);
        var outputs=java.util.List.of(gas(500),gas(500));
        h.assertTrue(!basin.acceptOutputs(java.util.List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)),outputs,true),"collective gas output capacity is checked without overbooking");
        h.assertTrue(!basin.acceptOutputs(java.util.List.of(),outputs,false),"failed collective capacity cannot emit a partial recipe");
        h.assertTrue(clouds.amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),level.getGameTime())==999_999_250,"failed recipe preflight has no atmosphere side effects");
        var handler=level.getCapability(Capabilities.FluidHandler.BLOCK,pos,null);
        h.assertTrue(handler.fill(gas(1000),FluidAction.SIMULATE)==750,"incoming probe reports only available atmospheric capacity");
        h.assertTrue(handler.fill(gas(1000),FluidAction.EXECUTE)==750,"unaccepted incoming gas stays upstream");
        var tank=basin.getTanks().getSecond().getPrimaryHandler();tank.setFluid(gas(600));
        h.assertTrue(tank.getFluidAmount()==600,"already committed gas remains in real storage while atmosphere is saturated");
        h.assertTrue(!tank.writeToNBT(level.registryAccess(),new net.minecraft.nbt.CompoundTag()).isEmpty(),"blocked committed gas is saved normally");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void legacyGasSpoutBufferVentsLocallyAndNeverUsesConnectedStorage(GameTestHelper h) {
        var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState()
                .setValue(com.simibubi.create.content.processing.basin.BasinBlock.FACING,Direction.EAST));
        var target=pos.below().east();h.getLevel().setBlockAndUpdate(target,AllBlocks.FLUID_TANK.getDefaultState());
        var basin=(BasinBlockEntity)h.getLevel().getBlockEntity(pos);
        var buffer=((net.quiwh.cwi.pollution.GasBasinStorage)basin).cwi$gasOverflow();buffer.add(gas(600));
        h.getLevel().setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());
        net.quiwh.cwi.pollution.GasContainers.releaseStored(basin);basin.tick();
        h.assertTrue(buffer.size()==1&&buffer.getFirst().getAmount()==600,"blocked legacy overflow remains in the saved native buffer");
        h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,target,null).getFluidInTank(0).isEmpty(),"blocked legacy gas cannot bypass the outlet through a spout");
        h.getLevel().setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
        net.quiwh.cwi.pollution.GasContainers.releaseStored(basin);
        h.assertTrue(buffer.isEmpty()&&GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==600,"legacy overflow vents exactly once at the basin");
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void advancedPeriodicBasinProductsVentOnlyOnCommittedTransfers(GameTestHelper h) {
        var manager=h.getLevel().getRecipeManager();var previous=manager.getRecipes();
        var recipe=NativeAdvancedTestSupport.recipe(h,"create:mixing","gas_periodic","""
                {"ingredients":[{"item":"minecraft:sand"}],"results":[{"stack":{"id":"kubejs:ammonia","amount":100},"per_tick":2}],"processing_time":4}
                """);
        try {
            manager.replaceRecipes(java.util.List.of(recipe));var pos=position(h);h.getLevel().setBlockAndUpdate(pos,AllBlocks.BASIN.getDefaultState());
            var basin=(BasinBlockEntity)h.getLevel().getBlockEntity(pos);basin.getInputInventory().setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SAND));
            var items=net.quiwh.cwi.compat.processing.NativeStorage.items(basin.getInputInventory(),basin.getOutputInventory());
            var fluids=net.quiwh.cwi.compat.processing.NativeStorage.fluids(basin.getTanks().getFirst(),basin.getTanks().getSecond());
            var work=new net.quiwh.cwi.compat.processing.NativeWork();
            h.assertTrue(work.start(h.getLevel(),recipe,items,fluids),"advanced recipe preflight can plan gaseous outputs");
            h.assertTrue(GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==0,"advanced recipe selection does not emit gas");
            for(int n=0;n<4;n++)h.assertTrue(work.step(h.getLevel(),items,fluids,.25),"advanced periodic processing commits");
            h.assertTrue(work.finished()&&GasClouds.get(h.getLevel()).amount(pos.above(),GasProfiles.byId("kubejs:ammonia"),h.getLevel().getGameTime())==200,"periodic products release exactly at their committed boundaries");
            h.assertTrue(basin.getTanks().getSecond().isEmpty(),"advanced direct-storage writes leave no released gas in storage");
        }finally{manager.replaceRecipes(previous);}
        h.succeed();
    }

    @GameTest(template="machine_empty",templateNamespace=CwiCore.ID)
    public static void containerGasUsesThePipeJetAtItsActualRim(GameTestHelper h) {
        var level=h.getLevel();var pos=position(h);long old=level.getGameTime();
        try(var receiver=new GasPollutionGameTests.ParticleRecipient(h,pos)) {
            long now=(old/20+10)*20+1;
            for(var state:java.util.List.of(AllBlocks.BASIN.getDefaultState(),AllBlocks.ITEM_DRAIN.getDefaultState(),MachineBlocks.SOLAR_EVAPORATOR.get().defaultBlockState())) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(now);now+=20;
                level.setBlockAndUpdate(pos,state);receiver.packets.clear();
                var owner=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)level.getBlockEntity(pos);
                IFluidHandler handler=owner instanceof SolarEvaporatorBlockEntity solar?solar.fluids():
                        owner instanceof BasinBlockEntity basin?basin.getTanks().getFirst().getPrimaryHandler():
                                ((SmartFluidTankBehaviour)owner.getBehaviour(SmartFluidTankBehaviour.TYPE)).getPrimaryHandler();
                handler.fill(gas(500),FluidAction.SIMULATE);GasClouds.get(level).tick(level);
                h.assertTrue(receiver.packets.isEmpty(),"container simulation creates no smoke");
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                handler.fill(gas(500),FluidAction.EXECUTE);GasClouds.get(level).tick(level);
                h.assertTrue(receiver.packets.size()==1,"container commits use the existing single-puff per-tick budget");
                var packet=receiver.packets.getFirst();double rim=pos.getY()+state.getCollisionShape(level,pos).max(Direction.Axis.Y)+.015;
                h.assertTrue(Math.abs(packet.getY()-rim)<1e-6&&Math.abs(packet.getX()-pos.getX()-.5)<.031
                        &&Math.abs(packet.getZ()-pos.getZ()-.5)<.031,"smoke starts at the real rim rather than floating above a shallow tray");
                var velocity=new net.minecraft.world.phys.Vec3(packet.getXDist(),packet.getYDist(),packet.getZDist());
                h.assertTrue(velocity.length()>=.119999&&velocity.length()<=.180001&&velocity.y/velocity.length()>=.9659,
                        "container gas retains pipe jet speed and outward cone");
            }
        }finally{((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(old);}
        h.succeed();
    }
}
