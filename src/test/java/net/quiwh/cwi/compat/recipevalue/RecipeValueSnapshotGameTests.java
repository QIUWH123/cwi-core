package net.quiwh.cwi.compat.recipevalue;

import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.crafting.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("cwi") @PrefixGameTestTemplate(false)
public final class RecipeValueSnapshotGameTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void codecSnapshotsPreserveNativePredicatesAndComponentPatches(GameTestHelper helper) {
        var cx = KubeJS.getStartupScriptManager().contextFactory.enter();
        var original = new ItemStack(Items.IRON_INGOT, 3);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("before"));
        var item = (ItemRecipeValue)RecipeValues.create(cx, false, new Object[]{original});
        try {
            dev.latvian.mods.kubejs.util.JsonUtils.of(cx, item);
            throw new AssertionError("event.custom's native JSON boundary must not serialize descriptors");
        } catch (dev.latvian.mods.kubejs.error.KubeRuntimeException expected) {
            check(expected.getMessage().contains("JSON"), "unsupported descriptor JSON explains the boundary");
        }
        original.setCount(20); original.set(DataComponents.CUSTOM_NAME, Component.literal("after"));
        check(item.toStack(cx).getCount() == 3 && item.toStack(cx).getHoverName().getString().equals("before"), "private component stack snapshot");
        reject(() -> item.ingredient(cx), "concrete component input");
        ItemStack removed = new ItemStack(Items.IRON_PICKAXE); removed.remove(DataComponents.MAX_DAMAGE);
        var removal = (ItemRecipeValue)RecipeValues.create(cx, false, new Object[]{removed});
        check(!removal.toStack(cx).has(DataComponents.MAX_DAMAGE), "component removal retained");
        reject(() -> removal.ingredient(cx), "removal patch on input");
        var predicate = DataComponentIngredient.of(false, item.toStack(cx));
        var ingredient = (ItemRecipeValue)RecipeValues.create(cx, false, new Object[]{new SizedIngredient(predicate, 2)});
        check(ingredient.quantity == 2 && ingredient.ingredient(cx).test(item.toStack(cx)) && !ingredient.ingredient(cx).test(original), "partial component predicate snapshot");
        var exact = (ItemRecipeValue)RecipeValues.create(cx, false, new Object[]{DataComponentIngredient.of(true, item.toStack(cx))});
        check(exact.ingredient(cx).test(item.toStack(cx)) && !exact.ingredient(cx).test(original), "exact component predicate snapshot");
        var alternatives = (ItemRecipeValue)RecipeValues.create(cx, false, new Object[]{Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT)});
        check(alternatives.ingredient(cx).test(new ItemStack(Items.IRON_INGOT)) && alternatives.ingredient(cx).test(new ItemStack(Items.GOLD_INGOT)), "alternatives are not counted slots");
        reject(() -> alternatives.toStack(cx), "ingredient cannot become stack");
        var nativeFluid = new FluidStack(Fluids.WATER, 250); nativeFluid.set(DataComponents.CUSTOM_NAME, Component.literal("water before"));
        var fluid = (FluidRecipeValue)RecipeValues.create(cx, true, new Object[]{nativeFluid});
        nativeFluid.setAmount(999); nativeFluid.set(DataComponents.CUSTOM_NAME, Component.literal("water after"));
        check(fluid.toStack(cx).getAmount() == 250 && fluid.toStack(cx).get(DataComponents.CUSTOM_NAME).getString().equals("water before"), "fluid component snapshot");
        reject(() -> fluid.ingredient(cx), "concrete fluid component input");
        var fluidPredicate = DataComponentFluidIngredient.of(false, fluid.toStack(cx));
        var fluidInput = (FluidRecipeValue)RecipeValues.create(cx, true, new Object[]{fluidPredicate, 25});
        check(fluidInput.ingredient(cx).test(fluid.toStack(cx)) && !fluidInput.ingredient(cx).test(nativeFluid), "native fluid component predicate");
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void startupAndClientContextsDelegateNativeStaticMethods(GameTestHelper helper) {
        var client = KubeJS.getClientScriptManager();
        if (client == null) {
            client = clientManagerForTest();
        }
        for (var manager : java.util.List.of(KubeJS.getStartupScriptManager(), client)) {
            var cx = (KubeJSContext)manager.contextFactory.enter();
            Object result = cx.evaluateString(cx.topLevelScope, "(() => { const saved = Item.of; const fluid = Fluid.of; return Item('2x minecraft:iron_ingot').toStack().count === 2 && saved('minecraft:iron_ingot', 3).count === 3 && Fluid('25x minecraft:water').toStack().amount === 25 && fluid('minecraft:water', 50).amount === 50 })()", "callable-manager-test", 1, null);
            check(Boolean.TRUE.equals(result), "actual " + manager.scriptType + " manager callable/static behavior");
            Object binding = cx.topLevelScope.get(cx, "Item", cx.topLevelScope);
            CallableRecipeBinding.install(cx);
            check(binding == cx.topLevelScope.get(cx, "Item", cx.topLevelScope), "binding decoration is idempotent");
        }
        helper.succeed();
    }
    public static dev.latvian.mods.kubejs.script.ScriptManager clientManagerForTest() {
        var client = new dev.latvian.mods.kubejs.script.ScriptManager(dev.latvian.mods.kubejs.script.ScriptType.CLIENT);
        client.contextFactory = new dev.latvian.mods.kubejs.script.KubeJSContextFactory(client);
        var wrappers = new dev.latvian.mods.kubejs.script.TypeWrapperRegistry(client.scriptType, client.contextFactory.getTypeWrappers());
        for (var plugin : dev.latvian.mods.kubejs.plugin.KubeJSPlugins.getAll()) {
            plugin.registerTypeWrappers(wrappers);
            plugin.registerRecordDefaults(client.contextFactory::registerDefaultRecordProperties);
        }
        return client;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void reject(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + message);
    }
}
