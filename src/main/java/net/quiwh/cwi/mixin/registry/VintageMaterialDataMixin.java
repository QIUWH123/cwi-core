package net.quiwh.cwi.mixin.registry;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.quiwh.cwi.compat.VintageMaterialPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class VintageMaterialDataMixin {
    @Inject(method = "scanDirectory", at = @At("RETURN"))
    private static void cwi$removeMaterialData(ResourceManager manager, String directory, Gson gson,
                                               Map<ResourceLocation, JsonElement> definitions, CallbackInfo ci) {
        VintageMaterialPolicy.filterData(directory, definitions);
    }
}
