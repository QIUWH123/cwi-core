package net.quiwh.cwi.mixin.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwi.registry.RegistrationBlocker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Objects;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> {
    @Shadow @Final private Map<ResourceLocation, Holder.Reference<T>> byLocation;
    @Shadow @Final private Map<ResourceKey<T>, Holder.Reference<T>> byKey;
    @Shadow @Final private Map<T, Holder.Reference<T>> byValue;
    @Shadow private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;
    @Shadow protected abstract void validateWrite(ResourceKey<T> key);

    @SuppressWarnings("unchecked")
    @Inject(method = "register(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"), cancellable = true)
    private void cwi$preventRegistration(int id, ResourceKey<T> key, T value, RegistrationInfo info,
                                        CallbackInfoReturnable<Holder.Reference<T>> cir) {
        if (!RegistrationBlocker.blocks(key)) return;
        validateWrite(key);
        Objects.requireNonNull(value);
        if (byLocation.containsKey(key.location()) || byValue.containsKey(value))
            throw new IllegalStateException("Cannot prevent an already registered key or value: " + key);

        Holder.Reference<T> reference;
        if (unregisteredIntrusiveHolders != null) {
            // Item/Block constructors have already made these holders. Leaving them here breaks freeze().
            reference = unregisteredIntrusiveHolders.remove(value);
            if (reference == null) throw new IllegalStateException("Missing intrusive holder for blocked entry " + key);
            ((HolderReferenceAccessor<T>) (Object) reference).cwi$bindKey(key);
        } else {
            // A lookup may have requested this key before the registration attempt.
            reference = byKey.remove(key);
            if (reference == null)
                reference = Holder.Reference.createStandAlone(((Registry<T>) (Object) this).asLookup(), key);
            ((HolderReferenceAccessor<T>) (Object) reference).cwi$bindValue(value);
        }
        RegistrationBlocker.remember(key, reference);
        // No registry maps, numeric IDs, aliases or on-add callbacks receive the value.
        cir.setReturnValue(reference);
    }
}
