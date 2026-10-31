package net.quiwh.cwi.mixin.shadow;

import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.shadow.ShadowDropRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.terrunic.shadowdrop.util.ShadowStyle", remap = false)
public abstract class ShadowStyleMixin {
    @Inject(method = "getAlpha", at = @At("HEAD"), cancellable = true)
    private static void cwi$amount(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
        var alpha = ShadowDropRules.resolve(stack).alpha();
        if (alpha != null) callback.setReturnValue(alpha);
    }

    @Inject(method = "isTransparent", at = @At("HEAD"), cancellable = true)
    private static void cwi$transparent(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        var alpha = ShadowDropRules.resolve(stack).alpha();
        if (alpha != null) callback.setReturnValue(alpha == 0);
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private static void cwi$color(CallbackInfoReturnable<int[]> callback) {
        var rgb = ShadowDropRules.renderColor();
        if (rgb != null) callback.setReturnValue(new int[]{rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255});
    }
}
