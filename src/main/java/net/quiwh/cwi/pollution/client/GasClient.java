package net.quiwh.cwi.pollution.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.quiwh.cwi.CwiCore;
import net.quiwh.cwi.pollution.GasPollution;

@EventBusSubscriber(modid=CwiCore.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class GasClient {
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GasPollution.SMOKE.get(),GasParticle.DynamicProvider::new);
    }
    private GasClient() {}
}
