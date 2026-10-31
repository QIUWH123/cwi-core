package net.quiwh.cwi.compat;

import java.util.*;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.ItemWrapper;
import dev.latvian.mods.kubejs.fluid.FluidWrapper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.quiwh.cwi.compat.recipevalue.*;
import moe.wolfgirl.probejs.plugin.ProbeJSPlugin;
import moe.wolfgirl.probejs.plugin.Priority;
import moe.wolfgirl.probejs.plugin.builtins.Bindings;
import moe.wolfgirl.probejs.typescript.ClassPath;
import moe.wolfgirl.probejs.typescript.base.DocumentRegistrar;
import moe.wolfgirl.probejs.typescript.document.base.*;

/** Conditional plugin entry: no ProbeJS types are loaded when ProbeJS is absent. */
public final class CwiRecipeValuesProbeJS extends ProbeJSPlugin {
    @Override public Set<Class<?>> provideClassForDiscovery() {
        return Set.of(ItemRecipeValue.class, FluidRecipeValue.class, ItemWrapper.class, FluidWrapper.class,
                ItemStack.class, Ingredient.class, SizedIngredient.class, FluidStack.class, FluidIngredient.class, SizedFluidIngredient.class);
    }
    @Override public void transformClass(moe.wolfgirl.probejs.typescript.Documents.ClassDocument entry) {
        Class<?> type = entry.classInfo().clazz();
        if (type != ItemRecipeValue.class && type != FluidRecipeValue.class) return;
        for (Code member : entry.document().members) {
            if (!(member instanceof moe.wolfgirl.probejs.typescript.document.members.MethodDecl method) || method.params.size() != 1) continue;
            Type parameter = switch (method.name) {
                case "withChance", "withWeight" -> moe.wolfgirl.probejs.typescript.document.Types.NUMBER;
                case "perTick" -> moe.wolfgirl.probejs.typescript.document.Types.BOOLEAN;
                default -> null;
            };
            if (parameter != null) method.params.set(0, new moe.wolfgirl.probejs.typescript.document.members.ParamDecl(method.params.getFirst().name, parameter));
        }
    }
    @Override @Priority(-1000) public void addSidedDocuments(DocumentRegistrar registrar) {
        addBindings(registrar, Bindings.STARTUP_BINDINGS, dev.latvian.mods.kubejs.KubeJS.getStartupScriptManager());
        addBindings(registrar, Bindings.CLIENT_BINDINGS, dev.latvian.mods.kubejs.KubeJS.getClientScriptManager());
        addBindings(registrar, Bindings.SERVER_BINDINGS, moe.wolfgirl.probejs.utils.GameUtils.getServerScriptManager());
        CwiRecipeValueProbeSchemas.add(registrar);
    }
    private static void addBindings(DocumentRegistrar registrar, ClassPath path, dev.latvian.mods.kubejs.script.ScriptManager manager) {
        if (manager != null) registrar.addGlobal(path, dumpBindings(path, manager));
    }
    /** ProbeJS's native global document shape, using each existing manager's actual bindings. */
    public static Code dumpBindings(ClassPath path, dev.latvian.mods.kubejs.script.ScriptManager manager) {
        var cx = (dev.latvian.mods.kubejs.script.KubeJSContext)manager.contextFactory.enter();
        var scope = cx.topLevelScope;
        var converter = new moe.wolfgirl.probejs.typescript.transpiler.TypeConverter();
        var fields = new ArrayList<Code>();
        for (Object key : scope.getIds(cx)) {
            if (!(key instanceof String name)) continue;
            Object value = scope.get(cx, name, scope);
            if (value instanceof dev.latvian.mods.rhino.NativeJavaClass clazz) value = clazz.getClassObject();
            else value = cx.jsToJava(value, dev.latvian.mods.rhino.type.TypeInfo.OBJECT);
            if (value == null || value instanceof dev.latvian.mods.rhino.BaseFunction || value instanceof dev.latvian.mods.kubejs.event.EventGroupWrapper) continue;
            Class<?> type = value instanceof Class<?> clazz ? clazz : value.getClass();
            while (!java.lang.reflect.Modifier.isPublic(type.getModifiers()) && type.getSuperclass() != null) type = type.getSuperclass();
            Type declaration = converter.convertType(dev.latvian.mods.rhino.type.TypeInfo.of(type));
            if (value instanceof Class<?>) declaration = moe.wolfgirl.probejs.typescript.document.Types.typeOf(declaration);
            if (name.equals("global")) declaration = moe.wolfgirl.probejs.typescript.document.Types.raw("{}");
            var field = new moe.wolfgirl.probejs.typescript.document.members.FieldDecl(name, declaration, true);
            field.setKind(moe.wolfgirl.probejs.typescript.document.base.KindAware.Kind.NAMESPACE);
            fields.add(field);
        }
        return new CallableBindings(new Bindings.Combined(fields, (ClassPath)path.getPackage()));
    }
    public static final class CallableBindings extends Code {
        private final Code original;
        private static final List<Class<?>> CLASSES = List.of(ItemRecipeValue.class, FluidRecipeValue.class,
                ItemStack.class, Ingredient.class, SizedIngredient.class, FluidStack.class, FluidIngredient.class, SizedFluidIngredient.class);
        public CallableBindings(Code original) { this.original = original; }
        @Override public Set<ClassPath> getImports() {
            Set<ClassPath> imports = new HashSet<>(original.getImports());
            for (Class<?> type : CLASSES) imports.add(new ClassPath(type));
            return imports;
        }
        @Override public void setResolvedSymbols(Map<ClassPath, String> symbols) { super.setResolvedSymbols(symbols); original.setResolvedSymbols(symbols); }
        private String symbol(Class<?> type) { return resolveSymbol(new ClassPath(type)); }
        @Override public List<String> format(int indent) {
            var lines = new ArrayList<>(original.format(indent));
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (boolean fluid : new boolean[]{false, true}) {
                    String name = fluid ? "Fluid" : "Item";
                    int at = line.indexOf("let " + name + ":");
                    if (at < 0) at = line.indexOf("const " + name + ":");
                    if (at < 0) continue;
                    int colon = line.indexOf(':', at);
                    String nativeType = line.substring(colon + 1).trim().replaceAll(";$", "");
                    String result = symbol(fluid ? FluidRecipeValue.class : ItemRecipeValue.class);
                    String one = fluid ? symbol(FluidStack.class) + " | " + symbol(SizedFluidIngredient.class)
                            : symbol(ItemStack.class) + " | " + symbol(Ingredient.class) + " | " + symbol(SizedIngredient.class);
                    String two = symbol(fluid ? FluidIngredient.class : Ingredient.class);
                    line = line.substring(0, colon + 1) + " (" + nativeType + ") & { (value: string | " + one + " | " + result + "): " + result
                            + "; (value: string | " + two + ", quantity: number): " + result + " };";
                }
                lines.set(i, line);
            }
            return lines;
        }
    }
}
