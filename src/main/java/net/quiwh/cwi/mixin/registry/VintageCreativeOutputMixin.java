package net.quiwh.cwi.mixin.registry;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.event.EventHooks;
import net.quiwh.cwi.registry.RegistrationBlocker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EventHooks.class)
public abstract class VintageCreativeOutputMixin {
    @WrapOperation(method = "onCreativeModeTabBuildContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;accept(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;Lnet/minecraft/world/item/CreativeModeTab$Output;)V"))
    private static void cwi$filterBeforeNeoForgeValidation(CreativeModeTab.DisplayItemsGenerator generator,
            CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output, Operation<Void> original,
            CreativeModeTab tab, ResourceKey<CreativeModeTab> key, CreativeModeTab.DisplayItemsGenerator originalGenerator,
            CreativeModeTab.ItemDisplayParameters originalParameters, CreativeModeTab.Output originalOutput) {
        boolean vintage = key.location().getNamespace().equals("vintageimprovements");
        original.call(generator, parameters, (CreativeModeTab.Output) (stack, visibility) -> {
            // Removed block items resolve to AIR through Block.asItem(), losing their original key.
            if ((vintage && stack.isEmpty()) || RegistrationBlocker.blocks(stack)) return;
            output.accept(stack, visibility);
        });
    }
}
