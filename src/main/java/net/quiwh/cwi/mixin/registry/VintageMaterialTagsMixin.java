package net.quiwh.cwi.mixin.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.quiwh.cwi.compat.VintageMaterialPolicy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public abstract class VintageMaterialTagsMixin {
    @Shadow @Final private String directory;

    @Inject(method = "load", at = @At("RETURN"))
    private void cwi$removeMaterialTagEntries(ResourceManager manager,
            CallbackInfoReturnable<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> cir) {
        var registry = switch (directory) {
            case "tags/item" -> Registries.ITEM.location();
            case "tags/block" -> Registries.BLOCK.location();
            case "tags/fluid" -> Registries.FLUID.location();
            case "tags/neoforge/fluid_type" -> ResourceLocation.fromNamespaceAndPath("neoforge", "fluid_type");
            default -> null;
        };
        if (registry != null) cir.getReturnValue().values().forEach(entries -> entries.removeIf(entry ->
                !entry.entry().isTag() && VintageMaterialPolicy.blocks(registry, entry.entry().getId())));
    }
}
