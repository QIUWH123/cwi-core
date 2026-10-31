package net.quiwh.cwi.mixin.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.BaseMappedRegistry;
import net.quiwh.cwi.registry.RegistrationBlocker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseMappedRegistry.class)
public abstract class RegistryAliasMixin<T> {
    @SuppressWarnings("unchecked")
    @Inject(method = "addAlias", at = @At("HEAD"), cancellable = true)
    private void cwi$preventAlias(ResourceLocation from, ResourceLocation to, CallbackInfo ci) {
        if (RegistrationBlocker.blocks(((Registry<T>) (Object) this).key().location(), from)) ci.cancel();
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "resolve(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void cwi$preventExistingAliasResolution(ResourceLocation name, CallbackInfoReturnable<ResourceLocation> cir) {
        if (RegistrationBlocker.blocks(((Registry<T>) (Object) this).key().location(), name))
            cir.setReturnValue(name);
    }
}
