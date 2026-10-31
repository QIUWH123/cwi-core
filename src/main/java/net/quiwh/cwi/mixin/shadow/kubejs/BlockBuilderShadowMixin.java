package net.quiwh.cwi.mixin.shadow.kubejs;

import dev.latvian.mods.kubejs.block.BlockBuilder;
import net.quiwh.cwi.compat.ShadowConfigurable;
import net.quiwh.cwi.shadow.ShadowDropRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BlockBuilder.class, remap = false)
public abstract class BlockBuilderShadowMixin implements ShadowConfigurable<BlockBuilder> {
    @Unique private ShadowDropRules.Style cwi$shadowStyle = ShadowDropRules.Style.INHERIT;
    @Override public ShadowDropRules.Style cwi$getShadowStyle() { return cwi$shadowStyle; }
    @Override public void cwi$setShadowStyle(ShadowDropRules.Style style) { cwi$shadowStyle = style; }
}
