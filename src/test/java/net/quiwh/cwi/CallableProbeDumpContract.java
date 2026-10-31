package net.quiwh.cwi;

import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import moe.wolfgirl.probejs.typescript.*;
import moe.wolfgirl.probejs.typescript.base.*;
import moe.wolfgirl.probejs.typescript.document.base.*;
import moe.wolfgirl.probejs.typescript.transpiler.*;
import moe.wolfgirl.probejs.java.members.ClassInfo;
import net.quiwh.cwi.compat.CwiRecipeValuesProbeJS;
import net.quiwh.cwi.compat.recipevalue.*;

/** Loaded only after the optional ProbeJS guard in CallableProbeGameTests. */
public final class CallableProbeDumpContract {
    public static void verify(GameTestHelper helper) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("probejs")) { helper.succeed(); return; }
        var plugin = new CwiRecipeValuesProbeJS();
        var globals = new HashMap<ClassPath, Code>();
        var recipes = new HashMap<ClassPath, Code>();
        plugin.addSidedDocuments(new DocumentRegistrar() {
            public void addGlobal(ClassPath path, Code code) { globals.put(path, code); }
            public void addDocument(ClassPath path, Code code) { recipes.put(path, code); }
            public void addInputAlias(ClassPath path, Type type) {}
        });
        check(globals.size() >= 2, "existing startup/server globals must be dumped");
        if (dev.latvian.mods.kubejs.KubeJS.getClientScriptManager() == null) {
            var client = net.quiwh.cwi.compat.recipevalue.RecipeValueSnapshotGameTests.clientManagerForTest();
            globals.put(moe.wolfgirl.probejs.plugin.builtins.Bindings.CLIENT_BINDINGS,
                CwiRecipeValuesProbeJS.dumpBindings(moe.wolfgirl.probejs.plugin.builtins.Bindings.CLIENT_BINDINGS, client));
        }
        var output = Path.of("../callable-api-implementation/probe-dump"); Files.createDirectories(output);
        for (var entry : globals.entrySet()) {
            var symbols = new HashMap<ClassPath, String>();
            for (ClassPath path : entry.getValue().getImports()) symbols.put(path, path.getClassName());
            entry.getValue().setResolvedSymbols(symbols);
            String text = String.join("\n", entry.getValue().format());
            String item = text.lines().filter(line -> line.contains("let Item:")).findFirst().orElseThrow();
            String fluid = text.lines().filter(line -> line.contains("let Fluid:")).findFirst().orElseThrow();
            check(item.contains("typeof") && item.contains("quantity: number") && item.contains("ItemRecipeValue") && item.contains("SizedIngredient"), "native static type intersected with callable item overloads");
            check(fluid.contains("typeof") && fluid.contains("FluidRecipeValue") && fluid.contains("SizedFluidIngredient"), "callable fluid signatures");
            Files.writeString(output.resolve(entry.getKey().asJavaPath() + ".d.ts"), text);
        }
        for (var entry : recipes.entrySet()) {
            var certified = Set.of("cwi:mixing_vessel_mixing", "cwi:blast_furnace_processing", "cwi:incubating",
                "create:mixing", "create:filling", "create:deploying", "create:pressing", "create:sequenced_assembly");
            boolean factory = entry.getKey().equals(moe.wolfgirl.probejs.plugin.builtins.events.RecipeEvents.RECIPES);
            if (!factory && certified.stream().noneMatch(id -> {
                String[] parts = id.split(":");
                return entry.getKey().equals(moe.wolfgirl.probejs.plugin.builtins.events.RecipeEvents.recipeClass(parts[0], parts[1]));
            })) continue;
            var symbols = new HashMap<ClassPath, String>(); for (var path : entry.getValue().getImports()) symbols.put(path, path.getClassName());
            entry.getValue().setResolvedSymbols(symbols);
            String text = String.join("\n", entry.getValue().format());
            check(text.contains("ItemRecipeValue"), "certified recipe constructor/setter descriptor types");
            if (factory || entry.getKey().equals(moe.wolfgirl.probejs.plugin.builtins.events.RecipeEvents.recipeClass("cwi", "mixing_vessel_mixing")))
                check(text.contains("FluidRecipeValue"), "mixed recipe fields include fluid descriptors");
            Files.writeString(output.resolve(entry.getKey().getClassName() + ".d.ts"), text);
        }
        var transpiler = new Transpiler(new TypeConverter());
        for (Class<?> type : plugin.provideClassForDiscovery()) {
            var info = ClassInfo.resolve(type); var document = transpiler.convert(info);
            plugin.transformClass(new Documents.ClassDocument(info, document, new TypeConverter(), List.of(), List.of(), List.of()));
            var symbols = new HashMap<ClassPath, String>(); for (var path : document.getImports()) symbols.put(path, path.getClassName());
            document.setResolvedSymbols(symbols);
            String text = String.join("\n", document.format());
            if (type == ItemRecipeValue.class) {
                check(text.contains("withChance(") && text.contains("withWeight(") && text.contains("perTick(") && text.contains("toStack("), "item descriptor declaration");
                check(!text.contains("probability: any"), "numeric option types");
            }
            if (type == FluidRecipeValue.class) check(!text.contains("withWeight("), "fluid descriptor has no weight method");
            Files.writeString(output.resolve(type.getSimpleName() + ".d.ts"), text);
        }
        helper.succeed();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
