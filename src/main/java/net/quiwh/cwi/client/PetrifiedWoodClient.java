package net.quiwh.cwi.client;

import net.minecraft.client.renderer.Sheets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.quiwh.cwi.CwiCore;
import net.quiwh.cwi.block.PetrifiedWood;

@EventBusSubscriber(modid = CwiCore.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetrifiedWoodClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> Sheets.addWoodType(PetrifiedWood.WOOD)); }
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(PetrifiedWood.DRIPPING_AMBER.get(), sprites -> new AmberParticle.Provider(sprites, AmberParticle.Stage.DRIPPING));
        event.registerSpriteSet(PetrifiedWood.FALLING_AMBER.get(), sprites -> new AmberParticle.Provider(sprites, AmberParticle.Stage.FALLING));
        event.registerSpriteSet(PetrifiedWood.LANDING_AMBER.get(), sprites -> new AmberParticle.Provider(sprites, AmberParticle.Stage.LANDING));
    }
    private PetrifiedWoodClient() {}
}
