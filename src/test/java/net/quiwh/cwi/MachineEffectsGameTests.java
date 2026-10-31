package net.quiwh.cwi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.machine.MachineBlocks;
import net.quiwh.cwi.machine.MachineEffects;
import net.quiwh.cwi.machine.MixingVesselBlockEntity;
import net.quiwh.cwi.machine.StirlingEngineBlockEntity;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class MachineEffectsGameTests {
    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void machineParticlesAndSoundsHaveNativeRegistrations(GameTestHelper h) {
        for (String id : List.of("breakdown_smoke","flamethrower_burst","flamethrower_flame","flamethrower_soul_flame","steam_cloud"))
            h.assertTrue(BuiltInRegistries.PARTICLE_TYPE.containsKey(ResourceLocation.parse("cwi:"+id)), "native particle registration: "+id);
        for (String id : List.of("drilling","oxygen_intake","oxygen_outtake"))
            h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.parse("cwi:"+id)), "native sound registration: "+id);
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void stirlingHeatTransitionsPlayOriginalSounds(GameTestHelper h) {
        BlockPos pos=new BlockPos(3,2,3);
        h.setBlock(pos,MachineBlocks.STIRLING_ENGINE.get());
        StirlingEngineBlockEntity engine=h.getBlockEntity(pos);
        var sounds=new ArrayList<ResourceLocation>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener=event -> {
            if (event.getLevel()==h.getLevel() && event.getSound()!=null) sounds.add(event.getSound().value().getLocation());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.setBlock(pos.below(),Blocks.MAGMA_BLOCK); engine.tick();
            engine.tick(); // No duplicate transition sound when heat is unchanged.
            h.setBlock(pos.below(),Blocks.STONE); engine.tick();
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(sounds.stream().filter(ResourceLocation.parse("cwi:oxygen_intake")::equals).count()==1
            && sounds.stream().filter(ResourceLocation.parse("cwi:oxygen_outtake")::equals).count()==1,
            "Stirling acceleration and deceleration use original sounds once per speed change");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void mixingStartsWithCreateWhirlpoolSound(GameTestHelper h) {
        BlockPos pos=new BlockPos(3,2,3); h.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity mixer=h.getBlockEntity(pos);
        mixer.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,8));
        mixer.fluids().fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE);
        var sounds=new ArrayList<ResourceLocation>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener=event -> {
            if(event.getLevel()==h.getLevel() && event.getSound()!=null) sounds.add(event.getSound().value().getLocation());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.setBlock(pos.above(),com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,net.minecraft.core.Direction.DOWN));
            com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor=h.getBlockEntity(pos.above());
            motor.generatedSpeed.setValue(128);motor.initialize();mixer.attachKinetics();mixer.tick();
        }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(sounds.contains(ResourceLocation.parse("minecraft:block.bubble_column.whirlpool_ambient")),
            "active mixing uses the same fluid mixing sound as Create's mixer");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void furnaceEffectsUseOriginalHeatBoundaries(GameTestHelper h) {
        h.assertTrue(MachineEffects.furnaceParticle(299)==null,"no secondary exhaust below smouldering");
        h.assertTrue(MachineEffects.furnaceParticle(300)==net.minecraft.core.particles.ParticleTypes.SMOKE
                && MachineEffects.furnaceParticle(1199)==net.minecraft.core.particles.ParticleTypes.SMOKE,
                "smouldering uses smoke instead of flames");
        h.assertTrue(MachineEffects.furnaceParticle(1200)==MachineEffects.FLAMETHROWER_FLAME.get()
                && MachineEffects.furnaceParticle(1899)==MachineEffects.FLAMETHROWER_FLAME.get(),"heated uses copied ordinary flame");
        h.assertTrue(MachineEffects.furnaceParticle(1900)==MachineEffects.FLAMETHROWER_SOUL_FLAME.get(),"superheated uses copied soul flame");
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void mixingParticlesUseContentsAndCreateMotion(GameTestHelper h) {
        BlockPos pos=new BlockPos(3,2,3); h.setBlock(pos,MachineBlocks.MIXING_VESSEL.get());
        MixingVesselBlockEntity mixer=h.getBlockEntity(pos);
        h.assertTrue(MachineEffects.mixingParticles(mixer).isEmpty(),"empty vessel emits no invented content particles");
        mixer.items().setStackInSlot(0,new ItemStack(Items.IRON_INGOT));
        mixer.fluids().fill(new FluidStack(Fluids.WATER,100),IFluidHandler.FluidAction.EXECUTE);
        for(int speed : new int[]{128,-128}) {
            mixer.setSpeed(speed);
            var particles=MachineEffects.mixingParticles(mixer);
            h.assertTrue(particles.size()==2 && particles.get(0).particle() instanceof net.minecraft.core.particles.ItemParticleOption
                && particles.get(1).particle() instanceof com.simibubi.create.content.fluids.particle.FluidParticleData,
                "same item and fluid particle options as Create mixer");
            var center=net.minecraft.world.phys.Vec3.atCenterOf(mixer.getBlockPos());
            for(var particle:particles) {
                // Create rotates through Minecraft's float sine table; allow its radius rounding.
                h.assertTrue(Math.abs(particle.position().distanceTo(center)-.25)<.00003
                    && Math.abs(particle.velocity().y-.25)<.008,"splash sits inside vessel with Create's upward tangential velocity: radius="+particle.position().distanceTo(center)+", y="+particle.velocity().y);
                var radial=particle.position().subtract(center);
                double turn=radial.z*particle.velocity().x-radial.x*particle.velocity().z;
                h.assertTrue(turn*speed>0,"splash rotation follows positive and negative mixer speed");
            }
        }
        h.succeed();
    }

    @GameTest(template="machine_empty", templateNamespace=CwiCore.ID)
    public static void stirlingSteamAlternatesAndRotates(GameTestHelper h) {
        BlockPos pos=new BlockPos(2,3,4);
        for(var face:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var first=MachineEffects.stirlingJets(pos,face,false);
            var second=MachineEffects.stirlingJets(pos,face,true);
            h.assertTrue(first.size()==2 && second.size()==2 && !first.equals(second),"two alternating jet pairs for every facing");
            for(var jet:java.util.stream.Stream.concat(first.stream(),second.stream()).toList())
                h.assertTrue(Math.abs(jet.y-(pos.getY()+.6))<.00001
                        && jet.x>=pos.getX()-.1 && jet.x<=pos.getX()+1.1
                        && jet.z>=pos.getZ()-.1 && jet.z<=pos.getZ()+1.1,"original jet height and face offsets");
        }
        h.succeed();
    }
}
