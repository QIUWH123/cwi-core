package net.quiwh.cwi.compat;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.typings.Info;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.quiwh.cwi.shadow.ShadowDropRules;

/** Rebuilt after every client-script load; never writes the user's Shadow Drop config. */
public final class ShadowDropEvent implements KubeEvent {
    private Integer alpha, rgb;
    private boolean open = true;

    private ShadowDropEvent() {}

    @Info("Default GUI item-shadow opacity: 0 disables shadows; 1 is fully opaque. Client scripts only.")
    public ShadowDropEvent amount(double amount) {
        checkOpen(); alpha = ShadowDropRules.alpha(amount); return this;
    }
    @Info("Default GUI item-shadow color as #RRGGBB.")
    public ShadowDropEvent color(String color) {
        checkOpen(); rgb = ShadowDropRules.color(color); return this;
    }
    @Info("Default GUI item-shadow color as 0xRRGGBB.")
    public ShadowDropEvent color(double color) {
        checkOpen(); rgb = ShadowDropRules.color(color); return this;
    }

    private void checkOpen() {
        if (!open) throw new IllegalStateException("Shadow defaults can only be changed inside CwiEvents.shadowDrop");
    }

    @HideFromJS public static void postClient() {
        var event = new ShadowDropEvent();
        try {
            CwiKubeJSEvents.SHADOW_DROP.post(event);
        } finally {
            event.open = false;
        }
        ShadowDropRules.defaults(new ShadowDropRules.Style(event.alpha, event.rgb));
    }
}
