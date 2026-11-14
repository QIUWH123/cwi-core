package net.quiwh.cwi.mixin.pack;

import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class AdvancementReloadMixin {
    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At("HEAD"), cancellable = true)
    private void cwi$skipAdvancements(ResourceManager resources, ProfilerFiller profiler,
            CallbackInfoReturnable<Map<ResourceLocation, JsonElement>> cir) {
        // Keep native application/reset logic and unrelated JSON listeners intact.
        if ((Object) this instanceof ServerAdvancementManager) cir.setReturnValue(Map.of());
    }
}
