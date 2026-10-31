package net.quiwh.cwi;

import dev.latvian.mods.kubejs.script.KubeJSContextFactory;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.ScriptableObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.quiwh.cwi.compat.CwiKubeJSEvents;
import net.quiwh.cwi.compat.CwiKubeJSPlugin;
import net.quiwh.cwi.shadow.ShadowDropRules;

/** Real startup builders/modifications and client-event dispatch, without client rendering classes. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class ShadowDropGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void shadowRulesHaveAClientScriptEvent(GameTestHelper helper) {
        helper.assertTrue(CwiKubeJSEvents.GROUP.getHandlers().containsKey("shadowDrop"),
                "Core must expose CwiEvents.shadowDrop in client scripts");
        helper.assertTrue(CwiKubeJSEvents.SHADOW_DROP.scriptTypePredicate.test(ScriptType.CLIENT)
                        && !CwiKubeJSEvents.SHADOW_DROP.scriptTypePredicate.test(ScriptType.SERVER)
                        && !CwiKubeJSEvents.SHADOW_DROP.scriptTypePredicate.test(ScriptType.STARTUP),
                "shadow defaults are a client-script event only");
        var manager = new ScriptManager(ScriptType.CLIENT);
        manager.canListenEvents = true;
        var cx = new KubeJSContextFactory(manager).enter();
        var scope = cx.initStandardObjects();
        ScriptableObject.putProperty(scope, "registerShadows", CwiKubeJSEvents.SHADOW_DROP, cx);
        cx.evaluateString(scope, """
            var retainedShadowEvent;
            registerShadows(event => {
                retainedShadowEvent = event;
                event.amount(0.4).color('#123456');
                for (let bad of [-0.1, 1.1, NaN, Infinity]) {
                    let rejected = false;
                    try { event.amount(bad); } catch (e) { rejected = true; }
                    if (!rejected) throw Error('Invalid amount accepted: ' + bad);
                }
                for (let bad of ['#123', '#gggggg', 'black', '#00112233', -1, 0x1000000, NaN, Infinity, 1.5]) {
                    let rejected = false;
                    try { event.color(bad); } catch (e) { rejected = true; }
                    if (!rejected) throw Error('Invalid color accepted: ' + bad);
                }
            });
            """, "shadow-drop-contract.js", 1, null);
        new CwiKubeJSPlugin().afterScriptsLoaded(manager);
        helper.assertTrue(ShadowDropRules.resolve(new ItemStack(Items.IRON_INGOT)).alpha() == 102
                        && ShadowDropRules.resolve(new ItemStack(Items.IRON_INGOT)).rgb() == 0x123456,
                "global opacity and string color are applied from the real script event");
        var item = stack("shadow_item_builder");
        var hammer = stack("shadow_hammer_builder");
        var block = stack("shadow_block_builder");
        helper.assertTrue(ShadowDropRules.resolve(item).alpha() == 0
                        && ShadowDropRules.resolve(item).rgb() == 0x123456,
                "ItemEvents.modification overrides registration amount while retaining its color");
        helper.assertTrue(ShadowDropRules.resolve(hammer).alpha() == 191
                        && ShadowDropRules.resolve(hammer).rgb() == 0xABCDEF,
                "custom hammer builders inherit fluent shadow methods and numeric colors");
        helper.assertTrue(ShadowDropRules.resolve(block).alpha() == 51
                        && ShadowDropRules.resolve(block).rgb() == 0xFEDCBA,
                "block registration transfers to its item, explicit item color wins, and BlockEvents.modification works");
        helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("cwi:shadow_no_item")),
                "configuring a shadow does not create an item for a noItem block");
        helper.assertTrue(ShadowDropRules.resolve(new ItemStack(Items.GOLD_INGOT)).alpha() == 255
                        && ShadowDropRules.resolve(new ItemStack(Items.GOLD_INGOT)).rgb() == 0x445566,
                "ItemEvents.modification also configures existing vanilla/mod items");
        cx.evaluateString(scope, """
            let rejected = false;
            try { retainedShadowEvent.amount(0); } catch (e) { rejected = true; }
            if (!rejected) throw Error('Retained event mutated live shadow defaults');
            """, "shadow-drop-closed-event.js", 1, null);
        var previous = ShadowDropRules.enterRender(hammer);
        try {
            helper.assertTrue(ShadowDropRules.renderColor() == 0xABCDEF, "per-item color reaches render scope");
            var nested = ShadowDropRules.enterRender(new ItemStack(Items.GOLD_INGOT));
            try { helper.assertTrue(ShadowDropRules.renderColor() == 0x445566, "nested item has its own color"); }
            finally { ShadowDropRules.leaveRender(nested); }
            helper.assertTrue(ShadowDropRules.renderColor() == 0xABCDEF, "nested scope restores outer item color");
        } finally { ShadowDropRules.leaveRender(previous); }
        helper.assertTrue(ShadowDropRules.renderColor() == 0x123456, "completed render restores global color");
        CwiKubeJSEvents.SHADOW_DROP.clear(ScriptType.CLIENT);
        new CwiKubeJSPlugin().afterScriptsLoaded(manager);
        helper.assertTrue(ShadowDropRules.resolve(new ItemStack(Items.IRON_INGOT)).alpha() == null
                        && ShadowDropRules.renderColor() == null,
                "removing client rules restores native defaults on reload");
        helper.assertTrue(ShadowDropRules.resolve(hammer).alpha() == 191,
                "client reload preserves startup registration/modification overrides");
        cx.evaluateString(scope, "registerShadows(event => event.amount(0.5).color(0));", "shadow-drop-reload.js", 1, null);
        new CwiKubeJSPlugin().afterScriptsLoaded(manager);
        helper.assertTrue(ShadowDropRules.resolve(new ItemStack(Items.IRON_INGOT)).alpha() == 128
                        && ShadowDropRules.renderColor() == 0,
                "reload replaces prior defaults and rounds half opacity to 128");
        CwiKubeJSEvents.SHADOW_DROP.clear(ScriptType.CLIENT);
        new CwiKubeJSPlugin().afterScriptsLoaded(manager);
        helper.succeed();
    }

    private static ItemStack stack(String path) {
        var id = ResourceLocation.fromNamespaceAndPath(CwiCore.ID, path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new AssertionError("Startup fixture did not register " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
