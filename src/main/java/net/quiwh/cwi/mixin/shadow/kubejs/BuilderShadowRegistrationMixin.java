package net.quiwh.cwi.mixin.shadow.kubejs;

import dev.latvian.mods.kubejs.block.BlockItemBuilder;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.world.item.Item;
import net.quiwh.cwi.compat.ShadowConfigurable;
import net.quiwh.cwi.shadow.ShadowDropRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attach styles to actual items, also covering inherited/custom item and block builders. */
@Mixin(value = BuilderBase.class, remap = false)
public abstract class BuilderShadowRegistrationMixin {
    @Inject(method = "createTransformedObject", at = @At("RETURN"))
    private void cwi$registerShadow(CallbackInfoReturnable<Object> callback) {
        if (!(callback.getReturnValue() instanceof Item item)) return;
        var own = (Object) this;
        var style = ShadowDropRules.Style.INHERIT;
        if (own instanceof BlockItemBuilder blockItem && blockItem.blockBuilder instanceof ShadowConfigurable<?> block)
            style = block.cwi$getShadowStyle();
        if (own instanceof ShadowConfigurable<?> configurable)
            style = style.overlay(configurable.cwi$getShadowStyle());
        if (!style.equals(ShadowDropRules.Style.INHERIT)) ShadowDropRules.itemStyle(item, style);
    }
}
