package net.quiwh.cwi.compat.probejs;

import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.schema.*;
import java.util.*;
import moe.wolfgirl.probejs.plugin.builtins.events.RecipeEvents;
import moe.wolfgirl.probejs.typescript.*;
import moe.wolfgirl.probejs.typescript.base.*;
import moe.wolfgirl.probejs.typescript.document.*;
import moe.wolfgirl.probejs.typescript.document.base.*;
import moe.wolfgirl.probejs.typescript.document.builders.*;
import moe.wolfgirl.probejs.typescript.document.members.*;
import moe.wolfgirl.probejs.typescript.transpiler.TypeConverter;
import net.quiwh.cwi.compat.kubejs.CwiApplicationRecipeSchemas;
import net.quiwh.cwi.compat.recipevalue.*;

/** Add descriptor types only to the certified recipe fields, without global stack aliases. */
public final class CwiRecipeValueProbeSchemas {
    private static final Set<String> BASE_TYPES = Set.of("cwi:mixing_vessel_mixing", "cwi:blast_furnace_processing", "cwi:incubating",
            "cwi:fluid_hazard", "cwi:inventory_hazard",
            "create:mixing", "create:filling", "create:deploying", "create:pressing", "create:sequenced_assembly", "create:item_application");
    private static final Set<String> TYPES;
    static { var types=new HashSet<>(BASE_TYPES); types.addAll(net.quiwh.cwi.compat.processing.NativeRecipeTypes.ALL); TYPES=Set.copyOf(types); }
    public static void add(DocumentRegistrar registrar) {
        var manager = moe.wolfgirl.probejs.utils.GameUtils.getServerScriptManager();
        if (manager == null) return;
        var converter = new TypeConverter();
        new RecipeEvents().addSidedDocuments(new DocumentRegistrar() {
            public void addGlobal(ClassPath p, Code code) { registrar.addGlobal(p, code); }
            public void addInputAlias(ClassPath p, Type type) { registrar.addInputAlias(p, type); }
            public void addDocument(ClassPath path, Code code) {
                for (String id : TYPES) {
                    String[] parts = id.split(":");
                    if (path.equals(RecipeEvents.recipeClass(parts[0], parts[1]))) {
                        var schema = manager.recipeSchemaStorage.namespaces.get(parts[0]).get(parts[1]).schema;
                        code = recipeClass(path, id, schema, converter); break;
                    }
                }
                if (path.equals(RecipeEvents.RECIPES)) {
                    // Rebuild namespace factories through public schema metadata. ProbeJS 8's
                    // private MappedDecl cannot be inspected before symbol resolution.
                    var document = new ClassBuilder(path.getClassName());
                    for (var namespaceEntry : manager.recipeSchemaStorage.namespaces.entrySet()) {
                        String namespace = namespaceEntry.getKey();
                        var methods = new ArrayList<MethodDecl>();
                        for (var entry : namespaceEntry.getValue().entrySet()) {
                            var type = entry.getValue(); var schema = type.schema;
                            if (type instanceof UnknownRecipeSchemaType || schema.isHidden()
                                    || !net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.containsKey(type.id)) continue;
                            if (TYPES.contains(type.id.toString())) {
                                for (var constructor : schema.constructors().values()) {
                                    var method = new MethodBuilder(entry.getKey()).returnType(Types.clazz(RecipeEvents.recipeClass(namespace, entry.getKey())));
                                    for (var key : constructor.keys) method.param(key.getPrimaryFunctionName(), parameter(type.id.toString(), key.name, key.component, converter));
                                    methods.add(method.build());
                                }
                            } else {
                                var method = new MethodBuilder(entry.getKey()).returnType(Types.clazz(RecipeEvents.recipeClass(namespace, entry.getKey())));
                                for (var key : schema.keys) if (!key.excluded && (key.functionNames == null || !key.functionNames.isEmpty()))
                                    method.param(key.getPrimaryFunctionName(), converter.convertType(key.component.typeInfo()).markAsInput(), false, key.optional());
                                methods.add(method.build());
                            }
                        }
                        if (!methods.isEmpty()) document.member(new NamespaceFactories(namespace, methods));
                    }
                    code = document.build();
                }
                registrar.addDocument(path, code);
            }
        });
    }
    private static ClassDecl recipeClass(ClassPath path, String type, RecipeSchema schema, TypeConverter converter) {
        var builder = new ClassBuilder(path.getClassName()).extendsType(converter.convertType(schema.recipeFactory.recipeType()));
        boolean hazard = Set.of("cwi:fluid_hazard", "cwi:inventory_hazard").contains(type);
        for (RecipeKey<?> key : schema.keys) for (String name : key.getValidFunctionNames()) {
            if (hazard && name.equals("protection")) continue;
            builder.method(name, method -> method.returnType(Types.THIS).param(name, parameter(type, key.name, key.component, converter)));
        }
        for (var function : schema.functions.values()) {
            if (hazard && function.name().equals("protection")) {
                builder.method("protection", method -> method.returnType(Types.THIS).param("id", Types.STRING)
                        .param("requiredResistance", Types.NUMBER, false, true));
                continue;
            }
            if (type.equals("create:item_application") && CwiApplicationRecipeSchemas.FUNCTIONS.contains(function.name())) {
                String name = function.name();
                builder.method(name, method -> {
                    method.returnType(Types.THIS);
                    switch (name) {
                        case "emptyHand", "keepBlock", "removeBlock" -> {}
                        case "sneaking" -> method.param("sneaking", Types.BOOLEAN, false, true);
                        case "priority", "damageHeldItem", "consumeHeldItem", "damagePlayer" -> method.param("amount", Types.NUMBER);
                        case "cooldown" -> method.param("time", Types.union(List.of(Types.NUMBER, Types.STRING)));
                        case "blockInput" -> { method.param("block", Types.STRING); method.param("state", Types.raw("Record<string, string>"), false, true); }
                        case "heldItem" -> method.param("ingredient", Types.union(List.of(Types.clazz(ItemRecipeValue.class), converter.convertType(dev.latvian.mods.rhino.type.TypeInfo.of(net.minecraft.world.item.crafting.Ingredient.class)).markAsInput())));
                        case "drops" -> method.param("drops", Types.parameterized(Types.raw("Array"), Types.union(List.of(Types.STRING, Types.clazz(ItemRecipeValue.class), Types.clazz(com.simibubi.create.content.processing.recipe.ProcessingOutput.class)))));
                        case "sound" -> { method.param("id", Types.STRING); method.param("volume", Types.NUMBER, false, true); method.param("pitch", Types.NUMBER, false, true); }
                        case "particle" -> method.param("particle", Types.raw("string | {id: string; block?: string; count?: number; dx?: number; dy?: number; dz?: number; speed?: number}"));
                        case "actions" -> method.param("actions", Types.raw("Array<Record<string, unknown>>"));
                    }
                });
                continue;
            }
            if ((type.equals("cwi:mixing_vessel_mixing") || type.equals("cwi:blast_furnace_processing") || net.quiwh.cwi.compat.processing.NativeRecipeTypes.supports(type)) && function.name().equals("priority")) {
                // The schema key already declares the checked integer setter.
                continue;
            }
            if ((type.equals("cwi:mixing_vessel_mixing") || type.equals("cwi:blast_furnace_processing") || type.equals("create:sequenced_assembly") || net.quiwh.cwi.compat.processing.NativeRecipeTypes.supports(type)) && function.name().equals("showOutputs")) {
                builder.method("showOutputs", method -> method.returnType(Types.THIS));
                builder.method("showOutputs", method -> method.returnType(Types.THIS).param("show", Types.BOOLEAN));
                continue;
            }
            if ((type.equals("cwi:mixing_vessel_mixing") || type.equals("cwi:blast_furnace_processing") || net.quiwh.cwi.compat.processing.NativeRecipeTypes.supports(type)) && function.name().equals("outputPool")) {
                builder.method("outputPool", method -> method.returnType(Types.THIS).param("entries",
                        Types.parameterized(Types.raw("Array"), Types.union(List.of(Types.clazz(ItemRecipeValue.class), Types.STRING)))));
                continue;
            }
            builder.method(function.name(), method -> {
                method.returnType(Types.THIS);
                for (int i = 0; i < function.arguments().size(); i++) {
                    String field = function.name();
                    RecipeComponent<?> argument = function.arguments().get(i);
                    if ((type.startsWith("cwi:") || net.quiwh.cwi.compat.processing.NativeRecipeTypes.supports(type)) && (field.equals("ingredients") || field.equals("results"))) {
                        var key = schema.constructors().values().stream().flatMap(c -> c.keys.stream())
                                .filter(candidate -> candidate.name.equals(field)).findFirst().orElse(null);
                        if (key != null) argument = key.component;
                    }
                    method.param("arg" + i, parameter(type, field, argument, converter));
                }
            });
        }
        return builder.build();
    }
    private static Type parameter(String type, String field, RecipeComponent<?> component, TypeConverter converter) {
        Type nativeType = converter.convertType(component.typeInfo()).markAsInput();
        if (Set.of("cwi:fluid_hazard", "cwi:inventory_hazard").contains(type)) return nativeType;
        boolean core = type.startsWith("cwi:");
        boolean input = field.equals("inputs") || field.endsWith("inputs") || field.equals("ingredients") || field.equals("ingredient");
        boolean output = field.equals("outputs") || field.endsWith("outputs") || field.equals("results") || field.equals("transitional_item");
        if (!input && !output) return nativeType;
        var options = new ArrayList<Type>(); options.add(nativeType);
        boolean fluidOnly = field.startsWith("fluid_");
        boolean nativeMachine=net.quiwh.cwi.compat.processing.NativeRecipeTypes.supports(type);
        boolean itemOnly = field.startsWith("item_") || type.equals("cwi:incubating") || (!core && (nativeMachine
                ? !(input?net.quiwh.cwi.compat.processing.NativeRecipeTypes.fluidInput(type):net.quiwh.cwi.compat.processing.NativeRecipeTypes.fluidOutput(type))
                : !type.equals("create:mixing") && !(input && type.equals("create:filling"))));
        if(nativeMachine && !(input?net.quiwh.cwi.compat.processing.NativeRecipeTypes.itemInput(type):net.quiwh.cwi.compat.processing.NativeRecipeTypes.itemOutput(type)))fluidOnly=true;
        if (!fluidOnly) options.add(Types.clazz(ItemRecipeValue.class));
        if (!itemOnly) options.add(Types.clazz(FluidRecipeValue.class));
        if (component instanceof ListRecipeComponent<?> list) {
            var entries = new ArrayList<Type>(); entries.add(converter.convertType(list.component().typeInfo()).markAsInput());
            entries.addAll(options.subList(1, options.size())); options.add(Types.parameterized(Types.raw("Array"), Types.union(entries)));
        } else if (core && (field.equals("ingredients") || field.equals("results"))) options.add(Types.parameterized(Types.raw("Array"), Types.union(List.copyOf(options.subList(1, options.size())))));
        return Types.union(options);
    }
    private static final class NamespaceFactories extends Code {
        final String namespace; final List<MethodDecl> methods;
        NamespaceFactories(String namespace, List<MethodDecl> methods) { this.namespace = namespace; this.methods = methods; }
        public Set<ClassPath> getImports() { var result = new HashSet<ClassPath>(); methods.forEach(m -> result.addAll(m.getImports())); return result; }
        public void setResolvedSymbols(Map<ClassPath,String> symbols) { super.setResolvedSymbols(symbols); methods.forEach(m -> m.setResolvedSymbols(symbols)); }
        public List<String> format(int indent) {
            var lines = new ArrayList<String>(); lines.add(" ".repeat(indent) + namespace + ": {");
            methods.forEach(m -> lines.addAll(m.format(indent + 4))); lines.add(" ".repeat(indent) + "};"); return lines;
        }
    }
    private CwiRecipeValueProbeSchemas() {}
}
