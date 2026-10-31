package net.quiwh.cwi.machine;

import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

/** Independently registered effects, using the original pack's particle textures and audio. */
public final class MachineEffects {
    private static final DeferredRegister<ParticleType<?>> PARTICLES=DeferredRegister.create(Registries.PARTICLE_TYPE,CwiCore.ID);
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,CwiCore.ID);
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> BREAKDOWN_SMOKE=particle("breakdown_smoke");
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> FLAMETHROWER_BURST=particle("flamethrower_burst");
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> FLAMETHROWER_FLAME=particle("flamethrower_flame");
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> FLAMETHROWER_SOUL_FLAME=particle("flamethrower_soul_flame");
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> STEAM_CLOUD=particle("steam_cloud");
    public static final DeferredHolder<SoundEvent,SoundEvent> DRILLING=sound("drilling");
    public static final DeferredHolder<SoundEvent,SoundEvent> OXYGEN_INTAKE=sound("oxygen_intake");
    public static final DeferredHolder<SoundEvent,SoundEvent> OXYGEN_OUTTAKE=sound("oxygen_outtake");

    private static DeferredHolder<ParticleType<?>,SimpleParticleType> particle(String id) {
        return PARTICLES.register(id,()->new SimpleParticleType(false));
    }
    private static DeferredHolder<SoundEvent,SoundEvent> sound(String id) {
        return SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(CwiCore.loadResource(id)));
    }
    public static void register(IEventBus bus) { PARTICLES.register(bus); SOUNDS.register(bus); }

    public static ParticleOptions furnaceParticle(double temperature) {
        return switch(FurnaceHeat.stage(temperature)) {
            case 1 -> ParticleTypes.SMOKE;
            case 2 -> FLAMETHROWER_FLAME.get();
            case 3 -> FLAMETHROWER_SOUL_FLAME.get();
            default -> null;
        };
    }

    /** count=0 keeps the original script's directed velocity rather than a Gaussian burst. */
    public static void directed(ServerLevel level,ParticleOptions particle,Vec3 pos,Vec3 velocity,double speed) {
        level.sendParticles(particle,pos.x,pos.y,pos.z,0,velocity.x,velocity.y,velocity.z,speed);
    }

    public record MixingParticle(ParticleOptions particle,Vec3 position,Vec3 velocity) {}

    /** Same item/fluid particle types and tangential motion as Create's Mechanical Mixer. */
    public static List<MixingParticle> mixingParticles(MixingVesselBlockEntity mixer) {
        var result=new ArrayList<MixingParticle>();
        if(mixer.getLevel()==null) return result;
        for(int slot=0;slot<mixer.items().getSlots();slot++) {
            var stack=mixer.getItem(slot);
            if(!stack.isEmpty()) result.add(mixingParticle(mixer,new ItemParticleOption(ParticleTypes.ITEM,stack)));
        }
        for(int tank=0;tank<mixer.fluids().getTanks();tank++) {
            var fluid=mixer.getFluid(tank);
            if(!fluid.isEmpty()) result.add(mixingParticle(mixer,new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(),fluid)));
        }
        return result;
    }
    private static MixingParticle mixingParticle(MixingVesselBlockEntity mixer,ParticleOptions particle) {
        var random=mixer.getLevel().random;
        Vec3 radial=VecHelper.rotate(new Vec3(0,0,.25),random.nextFloat()*360,Direction.Axis.Y);
        Vec3 velocity=VecHelper.rotate(radial,mixer.getSpeed()>0?25:-25,Direction.Axis.Y).add(0,.25,0).subtract(radial);
        velocity=VecHelper.offsetRandomly(velocity,random,1/128f);
        return new MixingParticle(particle,Vec3.atLowerCornerOf(mixer.getBlockPos()).add(.5,.5,.5).add(radial),velocity);
    }

    public static List<Vec3> stirlingJets(BlockPos pos,Direction facing,boolean alternate) {
        double[] offsets=switch(facing) {
            case EAST -> new double[]{.6,-.1,.2,1.1,.2,-.1,.6,1.1};
            case SOUTH -> new double[]{-.1,.2,1.1,.6,-.1,.6,1.1,.2};
            case WEST -> new double[]{.8,-.1,.4,1.1,.4,-.1,.8,1.1};
            default -> new double[]{-.1,.4,1.1,.8,-.1,.8,1.1,.4};
        };
        int start=alternate?0:4;
        return List.of(new Vec3(pos.getX()+offsets[start],pos.getY()+.6,pos.getZ()+offsets[start+1]),
            new Vec3(pos.getX()+offsets[start+2],pos.getY()+.6,pos.getZ()+offsets[start+3]));
    }
    private MachineEffects() {}
}
