package net.quiwh.cwi.mixin.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.quiwh.cwi.registry.RegistrationBlocker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeferredHolder.class)
public abstract class DeferredHolderMixin<R> {
    @Shadow @Final protected ResourceKey<R> key;
    @Shadow private Holder<R> holder;

    @Inject(method = "bind", at = @At("RETURN"))
    private void cwi$bindInternalReference(boolean throwOnMissingRegistry, CallbackInfo ci) {
        if (holder == null) holder = RegistrationBlocker.detached(key);
    }
}
