package net.quiwh.cwi.machine.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.quiwh.cwi.CwiCore;
import net.quiwh.cwi.machine.MachineBlocks;
import net.quiwh.cwi.machine.MachineEffects;

/** Kept behind a physical-client subscriber so dedicated servers never load rendering classes. */
@EventBusSubscriber(modid = CwiCore.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MachineClient {
    private static final PartialModel INCUBATOR = partial("incubator_blade");
    private static final PartialModel MIXER = partial("mixer_head");
    private static final PartialModel STIRLING = partial("stirling_engine_shaft");
    private static final PartialModel PUMP = partial("lava_extractor_pump_blade");
    private static final PartialModel SHAFT = partial("shaft");

    private static PartialModel partial(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CwiCore.ID, "block/" + path));
    }

    public static PartialModel incubatorBlade() { return INCUBATOR; }
    public static PartialModel mixerHead() { return MIXER; }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // Loading this subscriber constructs the partials before Flywheel's model-baking stage.
        ReinforcementTextures.init();
    }

    @SubscribeEvent
    public static void models(ModelEvent.ModifyBakingResult event) { ReinforcementTextures.wrap(event); }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MachineEffects.BREAKDOWN_SMOKE.get(),sprites -> new MachineParticle.Provider(sprites,MachineParticle.Kind.SMOKE));
        event.registerSpriteSet(MachineEffects.FLAMETHROWER_FLAME.get(),sprites -> new MachineParticle.Provider(sprites,MachineParticle.Kind.FLAME));
        event.registerSpriteSet(MachineEffects.FLAMETHROWER_SOUL_FLAME.get(),sprites -> new MachineParticle.Provider(sprites,MachineParticle.Kind.FLAME));
        event.registerSpriteSet(MachineEffects.FLAMETHROWER_BURST.get(),sprites -> new MachineParticle.Provider(sprites,MachineParticle.Kind.BURST));
        event.registerSpriteSet(MachineEffects.STEAM_CLOUD.get(),sprites -> new MachineParticle.Provider(sprites,MachineParticle.Kind.STEAM));
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MachineBlocks.INCUBATOR_ENTITY.get(), context -> new MachineRenderer<>(context, INCUBATOR, Direction.DOWN));
        event.registerBlockEntityRenderer(MachineBlocks.MIXING_VESSEL_ENTITY.get(), context -> new MachineRenderer<>(context, MIXER, Direction.UP));
        event.registerBlockEntityRenderer(MachineBlocks.STIRLING_ENGINE_ENTITY.get(), context -> new MachineRenderer<>(context, STIRLING, null));
        event.registerBlockEntityRenderer(MachineBlocks.LAVA_EXTRACTOR_PUMP_ENTITY.get(), context -> new MachineRenderer<>(context, PUMP, Direction.UP));
        event.registerBlockEntityRenderer(MachineBlocks.LAVA_EXTRACTOR_STRESS_INPUT_ENTITY.get(), context -> new MachineRenderer<>(context, SHAFT, Direction.UP));
        event.registerBlockEntityRenderer(MachineBlocks.LAVA_EXTRACTOR_PUMP_BLADE_ENTITY.get(), context -> new MachineRenderer<>(context, PUMP, Direction.UP));
        event.registerBlockEntityRenderer(MachineBlocks.SOLAR_EVAPORATOR_ENTITY.get(), context -> new MachineRenderer<>(context));
        event.registerBlockEntityRenderer(MachineBlocks.BLAST_FURNACE_ENTITY.get(), context -> new MachineRenderer<>(context));
    }

    private MachineClient() {}
}
