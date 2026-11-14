package net.quiwh.cwi.pollution;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

public final class GasPollution {
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,CwiCore.ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES=DeferredRegister.create(Registries.PARTICLE_TYPE,CwiCore.ID);
    public static final DeferredHolder<MobEffect,NeurotoxinEffect> NEUROTOXIN=EFFECTS.register("neurotoxin",NeurotoxinEffect::new);
    public static final DeferredHolder<ParticleType<?>,ParticleType<GasParticleOptions>> SMOKE=PARTICLES.register("gas_cloud",()->new ParticleType<GasParticleOptions>(false) {
        public com.mojang.serialization.MapCodec<GasParticleOptions> codec(){return GasParticleOptions.CODEC;}
        public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,GasParticleOptions> streamCodec(){return GasParticleOptions.STREAM_CODEC;}
    });
    public static GasParticleOptions particle(GasProfile gas){return new GasParticleOptions(gas);}
    public static void register(IEventBus bus) {
        EFFECTS.register(bus);PARTICLES.register(bus);
        bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event)->
            event.registrar("1").playToClient(GasDefinitionsPayload.TYPE,GasDefinitionsPayload.CODEC,
                (payload,context)->context.enqueueWork(()->GasProfiles.remoteFluids(payload.fluids()))));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent event)->{
            var payload=new GasDefinitionsPayload(GasProfiles.fluidIds());
            if(event.getPlayer()!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(event.getPlayer(),payload);
            else net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(payload);
        });
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(GasProfiles::freeze));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event)->GasProfiles.validateEnchantmentIds(event.getServer().registryAccess()));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event)->{
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                GasContainers.tick(level);
                var clouds=GasClouds.existing(level);if(clouds!=null) clouds.tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event)->resetMovement(event));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload event)->resetMovement(event));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post event)->{
            if(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living)GasRuleExposure.tickLiving(living);
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event)->GasExposure.tick(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish event)->{
            if(!event.getEntity().level().isClientSide && event.getItem().is(Items.MILK_BUCKET)) GasExposure.cure(event.getEntity());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(MobEffectEvent.Remove event)->{
            if(!event.isCanceled()) {
                var id=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(event.getEffect().value());
                if(id!=null)GasRuleExposure.cure(event.getEntity(),id);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(LivingDeathEvent event)->{
            if(!event.isCanceled()) GasExposure.cure(event.getEntity());
        });
    }
    private static void resetMovement(net.neoforged.neoforge.event.level.ChunkEvent event) {
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            var clouds=GasClouds.existing(level);if(clouds!=null)clouds.resetMovement(event.getChunk().getPos(),level.getGameTime());
        }
    }
    private GasPollution() {}
}
