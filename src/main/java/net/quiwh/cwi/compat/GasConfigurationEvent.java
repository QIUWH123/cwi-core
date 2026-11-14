package net.quiwh.cwi.compat;

import java.util.function.Consumer;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.quiwh.cwi.pollution.GasProfile;
import net.quiwh.cwi.pollution.GasProfiles;

/** Add gas behavior to an existing mod fluid without registering a replacement. */
public final class GasConfigurationEvent implements KubeEvent {
    private boolean open=true;
    private static boolean posted;
    public void modify(String fluidId,Consumer<GasProfile.Builder> configure) {
        if(!open)throw new IllegalStateException("Gas modification is startup-only");
        configure.accept(GasProfiles.configure(fluidId));
    }
    public void alias(String aliasId,String profileId) {
        if(!open)throw new IllegalStateException("Gas aliases are startup-only");
        GasProfiles.alias(aliasId,profileId);
    }
    public static void postStartup() {
        if(posted)return;
        posted=true;
        var event=new GasConfigurationEvent();
        try {CwiKubeJSEvents.GASES.post(event);}finally {event.open=false;}
    }
}
