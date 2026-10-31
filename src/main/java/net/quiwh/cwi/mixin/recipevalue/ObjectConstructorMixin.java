package net.quiwh.cwi.mixin.recipevalue;

import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.script.SourceLine;
import dev.latvian.mods.kubejs.util.*;
import dev.latvian.mods.rhino.Context;
import net.quiwh.cwi.compat.recipevalue.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import com.google.gson.*;

/** Native map constructors have a JSON path instead of the positional component path. */
@Mixin(value = RecipeTypeFunction.class, remap = false)
public abstract class ObjectConstructorMixin {
    @Inject(method = "createRecipe", at = @At("HEAD"))
    private void cwi$object(Context cx, SourceLine line, ErrorStack errors, Object[] args, CallbackInfoReturnable<KubeRecipe> cir) {
        var type = (RecipeTypeFunction)(Object)this;
        if (args.length != 1 || type.schemaType.schema.constructors().containsKey(1)
                || !(RecipeValues.unwrap(args[0]) instanceof Map<?,?> fields) || !RecipeValueAdapters.contains(fields)) return;
        var recipe = type.schemaType.schema.recipeFactory.create(type, line, true);
        var context = new RecipeScriptContext.Impl(cx, recipe, errors);
        var json = new JsonObject();
        for (var entry : fields.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!RecipeValueAdapters.contains(entry.getValue())) { json.add(name, JsonUtils.of(cx, entry.getValue())); continue; }
            RecipeKey<?> key = type.schemaType.schema.getOptionalKey(name);
            if (key == null) throw new IllegalArgumentException(type.idString + " unsupported descriptor field: " + name);
            json.add(key.name, encode(context, key, entry.getValue()));
        }
        args[0] = json;
    }
    private static <T> JsonElement encode(RecipeScriptContext cx, RecipeKey<T> key, Object raw) {
        T value = RecipeValueAdapters.wrap(cx, key, raw);
        return key.codec.encodeStart(cx.ops().json(), value).getOrThrow();
    }
}
