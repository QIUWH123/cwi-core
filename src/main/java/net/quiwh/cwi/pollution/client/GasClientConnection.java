package net.quiwh.cwi.pollution.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.quiwh.cwi.pollution.GasProfiles;

@EventBusSubscriber(modid="cwi",value=Dist.CLIENT)
public final class GasClientConnection {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){GasProfiles.remoteFluids(null);GasParticle.reset();}
    private GasClientConnection(){}
}
