package net.quiwh.cwi.mixin.registry;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.registry.RegistrationBlocker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Owning mods may retain detached objects; creative tabs must never expose them as usable items. */
@Mixin(targets = "net.minecraft.world.item.CreativeModeTab$ItemDisplayBuilder")
public abstract class CreativeMaterialMixin {
    @Inject(method = "accept", at = @At("HEAD"), cancellable = true)
    private void cwi$skipPreventedItem(ItemStack stack, CreativeModeTab.TabVisibility visibility, CallbackInfo ci) {
        if (RegistrationBlocker.blocks(stack)) ci.cancel();
    }
}
