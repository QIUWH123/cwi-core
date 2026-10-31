package net.quiwh.cwi.mixin.recipevalue;

import net.quiwh.cwi.compat.recipevalue.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.schema.function.*;
import dev.latvian.mods.rhino.*;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.*;
@Mixin(value = RecipeSchemaJSFunction.class, remap = false)
public abstract class SchemaFunctionMixin {
    @Shadow @Final public KubeRecipe recipe;
    @Shadow @Final public ResolvedRecipeSchemaFunction func;
    @Shadow @Final public TypeInfo[] argTypes;
    @Inject(method = "call(Ldev/latvian/mods/rhino/Context;Ldev/latvian/mods/rhino/Scriptable;Ldev/latvian/mods/rhino/Scriptable;[Ljava/lang/Object;)Ldev/latvian/mods/kubejs/recipe/KubeRecipe;", at = @At("HEAD"), cancellable = true)
    private void cwi$function(Context cx, Scriptable scope, Scriptable self, Object[] args, CallbackInfoReturnable<KubeRecipe> cir) {
        if (!RecipeValueAdapters.contains(args)) return;
        try {
        var context = new RecipeScriptContext.Impl(cx, recipe);
        String name = recipe.type.schemaType.schema.functions.values().stream().filter(f -> f.function() == func)
                .map(RecipeFunctionInstance::name).findFirst().orElse("unknown");
        if (recipe.type.idString.startsWith("cwi:") && Set.of("input", "inputs", "output", "outputs").contains(name)
                && args.length == 1) {
            func.execute(context, List.of(args[0]));
        } else if (func instanceof AddToListFunction.Resolved<?> list && args.length == 1) {
            // Adapt before native TypeInfo coercion, then retain the function's normal append behavior.
            Object value = RecipeValueAdapters.wrap(context, list.key(), args[0]);
            var combined = new ArrayList<Object>();
            Object previous = recipe.getValue(list.key());
            if (previous instanceof Collection<?> entries) combined.addAll(entries);
            combined.addAll((Collection<?>)value);
            RecipeValueAdapters.limits(context, list.key().name, combined);
            func.execute(context, List.of(value));
        } else throw new IllegalArgumentException(recipe.type.idString + " custom function " + name + " does not support descriptors");
        cir.setReturnValue(recipe);
        } catch (RuntimeException ex) { throw Context.throwAsScriptRuntimeEx(ex, cx); }
    }
}
