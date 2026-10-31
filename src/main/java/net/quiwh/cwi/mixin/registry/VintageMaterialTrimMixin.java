package net.quiwh.cwi.mixin.registry;

import com.google.gson.JsonElement;
import com.mojang.serialization.Decoder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegistryDataLoader.class)
public abstract class VintageMaterialTrimMixin {
    @Inject(method = "loadElementFromResource", at = @At("HEAD"), cancellable = true)
    private static <E> void cwi$skipVanadiumTrim(WritableRegistry<E> registry, Decoder<E> decoder,
            RegistryOps<JsonElement> ops, ResourceKey<E> key, Resource resource, RegistrationInfo info, CallbackInfo ci) {
        if (key.isFor(Registries.TRIM_MATERIAL) && key.location().equals(ResourceLocation.fromNamespaceAndPath("vintageimprovements", "vanadium"))) ci.cancel();
    }
}
