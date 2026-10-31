package net.quiwh.cwi.mixin.recipevalue;

import net.quiwh.cwi.compat.recipevalue.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
@Mixin(value = RecipeComponentValue.class, remap = false)
public abstract class ReplacementValuesMixin<T> {
    @Shadow @Final public RecipeKey<T> key;
    @Shadow public T value;
    @Shadow public abstract void write();
    @Inject(method = "replace", at = @At("HEAD"), cancellable = true)
    private void cwi$replace(RecipeScriptContext cx, ReplacementMatchInfo match, Object raw, CallbackInfoReturnable<Boolean> cir) {
        if (!(RecipeValues.unwrap(raw) instanceof RecipeValue descriptor)) return;
        @SuppressWarnings("unchecked") T next = (T)RecipeValueAdapters.replace(cx, key.name, key.component, value, match, descriptor);
        boolean changed = value != next;
        if (changed) { value = next; write(); }
        cir.setReturnValue(changed);
    }
}
