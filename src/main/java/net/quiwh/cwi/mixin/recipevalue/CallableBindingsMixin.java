package net.quiwh.cwi.mixin.recipevalue;

import net.quiwh.cwi.compat.recipevalue.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = dev.latvian.mods.kubejs.script.KubeJSContext.class, remap = false)
public abstract class CallableBindingsMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void cwi$bindings(CallbackInfo ci) {
        CallableRecipeBinding.install((dev.latvian.mods.kubejs.script.KubeJSContext)(Object)this);
    }
}
