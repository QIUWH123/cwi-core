package net.quiwh.cwi.mixin.recipevalue;

import net.quiwh.cwi.compat.recipevalue.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.rhino.Context;
@Mixin(value = KubeRecipe.class, remap = false)
public abstract class RecipeSetMixin {
    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void cwi$set(Context cx, String name, Object raw, CallbackInfoReturnable<KubeRecipe> cir) {
        if (!RecipeValueAdapters.contains(raw)) return;
        var recipe = (KubeRecipe)(Object)this;
        for (var value : recipe.getRecipeComponentValues()) if (value.key.names.contains(name)) {
            set(cx, recipe, value.key, raw); cir.setReturnValue(recipe); return;
        }
        throw new IllegalArgumentException("Unsupported descriptor field: " + name);
    }
    private static <T> void set(Context cx, KubeRecipe recipe, RecipeKey<T> key, Object raw) {
        recipe.setValue(key, RecipeValueAdapters.wrap(new RecipeScriptContext.Impl(cx, recipe), key, raw));
    }
}
