package net.quiwh.cwi.mixin.shadow.kubejs;

import dev.latvian.mods.kubejs.item.ItemBuilder;
import net.quiwh.cwi.compat.ShadowConfigurable;
import net.quiwh.cwi.shadow.ShadowDropRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ItemBuilder.class, remap = false)
public abstract class ItemBuilderShadowMixin implements ShadowConfigurable<ItemBuilder> {
    @Unique private ShadowDropRules.Style cwi$shadowStyle = ShadowDropRules.Style.INHERIT;
    @Override public ShadowDropRules.Style cwi$getShadowStyle() { return cwi$shadowStyle; }
    @Override public void cwi$setShadowStyle(ShadowDropRules.Style style) { cwi$shadowStyle = style; }
}
