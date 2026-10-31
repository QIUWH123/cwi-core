package net.quiwh.cwi.compat.recipevalue;

import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.rhino.*;
import dev.latvian.mods.rhino.util.DefaultValueTypeHint;

/** Change only function calls; static overloads and Java class behavior belong to KubeJS. */
public final class CallableRecipeBinding extends NativeJavaClass {
    private final NativeJavaClass original;
    private final boolean fluid;
    private CallableRecipeBinding(Context cx, Scriptable scope, NativeJavaClass original, boolean fluid) {
        super(cx, scope, original.getClassObject());
        this.original = original; this.fluid = fluid;
    }
    public static void install(KubeJSContext cx) {
        for (String name : new String[]{"Item", "Fluid"}) {
            Object binding = cx.topLevelScope.get(cx, name, cx.topLevelScope);
            if (binding instanceof NativeJavaClass nativeClass && !(binding instanceof CallableRecipeBinding))
                cx.topLevelScope.put(cx, name, cx.topLevelScope,
                        new CallableRecipeBinding(cx, cx.topLevelScope, nativeClass, name.equals("Fluid")));
        }
    }
    @Override public Object call(Context cx, Scriptable scope, Scriptable self, Object[] args) {
        try { return cx.wrap(scope, RecipeValues.create(cx, fluid, args)); }
        catch (RuntimeException ex) { throw Context.throwAsScriptRuntimeEx(ex, cx); }
    }
    @Override public Object get(Context cx, String name, Scriptable start) { return original.get(cx, name, original); }
    @Override public boolean has(Context cx, String name, Scriptable start) { return original.has(cx, name, original); }
    @Override public void put(Context cx, String name, Scriptable start, Object value) { original.put(cx, name, original, value); }
    @Override public Object[] getIds(Context cx) { return original.getIds(cx); }
    @Override public Object getDefaultValue(Context cx, DefaultValueTypeHint hint) { return original.getDefaultValue(cx, hint); }
    @Override public Scriptable construct(Context cx, Scriptable scope, Object[] args) { return original.construct(cx, scope, args); }
    @Override public boolean hasInstance(Context cx, Scriptable value) { return original.hasInstance(cx, value); }
    @Override public Object unwrap() { return original.unwrap(); }
}
