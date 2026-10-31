package net.quiwh.cwi.mixin.recipevalue;

import net.quiwh.cwi.compat.recipevalue.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.rhino.*;
@Mixin(value = RecipeComponentValueFunction.class, remap = false)
public abstract class GeneratedSetterMixin {
    @Shadow @Final public KubeRecipe recipe;
    @Shadow @Final public RecipeComponentValue<?> componentValue;
    @Inject(method = "call(Ldev/latvian/mods/rhino/Context;Ldev/latvian/mods/rhino/Scriptable;Ldev/latvian/mods/rhino/Scriptable;[Ljava/lang/Object;)Ldev/latvian/mods/kubejs/recipe/KubeRecipe;", at = @At("HEAD"), cancellable = true)
    private void cwi$setter(Context cx, Scriptable scope, Scriptable self, Object[] args, CallbackInfoReturnable<KubeRecipe> cir) {
        try {
            if (args.length > 0 && RecipeValueAdapters.contains(args[0])) cir.setReturnValue(set(cx, componentValue.key, args[0]));
        } catch (RuntimeException ex) { throw Context.throwAsScriptRuntimeEx(ex, cx); }
    }
    private <T> KubeRecipe set(Context cx, RecipeKey<T> key, Object value) {
        return recipe.setValue(key, RecipeValueAdapters.wrap(new RecipeScriptContext.Impl(cx, recipe), key, value));
    }
}
