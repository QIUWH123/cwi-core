package net.quiwh.cwi.compat;

import dev.latvian.mods.kubejs.typings.Info;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.quiwh.cwi.shadow.ShadowDropRules;

/** Shared fluent methods attached to the existing KubeJS builders/modification objects. */
public interface ShadowConfigurable<T> {
    @HideFromJS ShadowDropRules.Style cwi$getShadowStyle();
    @HideFromJS void cwi$setShadowStyle(ShadowDropRules.Style style);

    @Info("GUI item-shadow opacity from 0 (none) to 1 (fully opaque). Overrides the global default.")
    @SuppressWarnings("unchecked")
    default T shadowAmount(double amount) {
        cwi$setShadowStyle(cwi$getShadowStyle().overlay(new ShadowDropRules.Style(ShadowDropRules.alpha(amount), null)));
        return (T) this;
    }
    @Info("GUI item-shadow color as #RRGGBB. Does not change the item's own tint.")
    @SuppressWarnings("unchecked")
    default T shadowColor(String color) {
        cwi$setShadowStyle(cwi$getShadowStyle().overlay(new ShadowDropRules.Style(null, ShadowDropRules.color(color))));
        return (T) this;
    }
    @Info("GUI item-shadow color as 0xRRGGBB. Does not change the item's own tint.")
    @SuppressWarnings("unchecked")
    default T shadowColor(double color) {
        cwi$setShadowStyle(cwi$getShadowStyle().overlay(new ShadowDropRules.Style(null, ShadowDropRules.color(color))));
        return (T) this;
    }
}
