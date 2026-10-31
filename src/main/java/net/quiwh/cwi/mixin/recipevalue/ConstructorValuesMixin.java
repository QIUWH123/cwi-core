package net.quiwh.cwi.mixin.recipevalue;

import net.quiwh.cwi.compat.recipevalue.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = dev.latvian.mods.kubejs.recipe.component.ComponentValueMap.class, remap = false)
public abstract class ConstructorValuesMixin {
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private <T> void cwi$constructor(dev.latvian.mods.kubejs.recipe.RecipeScriptContext cx,
            dev.latvian.mods.kubejs.recipe.RecipeKey<T> key, CallbackInfoReturnable<T> cir) {
        Object raw = ((java.util.Map<?,?>)(Object)this).get(key);
        if (RecipeValueAdapters.contains(raw)) cir.setReturnValue(RecipeValueAdapters.wrap(cx, key, raw));
    }
}
