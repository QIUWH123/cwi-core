package net.quiwh.cwi;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Real startup-script/NeoForge registration fixtures, never included in the mod JAR. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class RegistrationBlockingGameTests {
    @EventBusSubscriber(modid = CwiCore.ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CwiCore.ID);
        static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CwiCore.ID);
        static final net.neoforged.neoforge.registries.DeferredBlock<Block> BLOCKED =
                BLOCKS.registerSimpleBlock("registration_blocked");
        static final net.neoforged.neoforge.registries.DeferredItem<BlockItem> BLOCKED_ITEM =
                ITEMS.registerSimpleBlockItem(BLOCKED);
        static final net.neoforged.neoforge.registries.DeferredBlock<Block> KEPT =
                BLOCKS.registerSimpleBlock("registration_kept");
        static final net.neoforged.neoforge.registries.DeferredItem<BlockItem> KEPT_ITEM =
                ITEMS.registerSimpleBlockItem(KEPT);
        static final net.neoforged.neoforge.registries.DeferredBlock<Block> ITEM_ONLY =
                BLOCKS.registerSimpleBlock("registration_item_only");
        static final net.neoforged.neoforge.registries.DeferredItem<BlockItem> ITEM_ONLY_ITEM =
                ITEMS.registerSimpleBlockItem(ITEM_ONLY);
        static final net.neoforged.neoforge.registries.DeferredBlock<Block> BLOCK_ONLY =
                BLOCKS.registerSimpleBlock("registration_block_only");
        static final net.neoforged.neoforge.registries.DeferredItem<Item> BLOCK_ONLY_ITEM =
                ITEMS.registerSimpleItem("registration_block_only");
        static Holder.Reference<Item> directItem;

        static {
            var bus = ModList.get().getModContainerById(CwiCore.ID).orElseThrow().getEventBus();
            BLOCKS.register(bus);
            ITEMS.register(bus);
        }

        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.BLOCK, helper -> {
                Block preconstructed = new Block(BlockBehaviour.Properties.of());
                helper.register(id("registration_preconstructed"), preconstructed);
                helper.register(id("registration_helper"), new Block(BlockBehaviour.Properties.of()));
            });
            if (event.getRegistryKey().equals(Registries.ITEM)) {
                directItem = Registry.registerForHolder(BuiltInRegistries.ITEM,
                        id("registration_direct"), new Item(new Item.Properties()));
                BuiltInRegistries.ITEM.addAlias(id("registration_blocked_alias"), id("registration_kept"));
                BuiltInRegistries.ITEM.addAlias(id("registration_kept_alias"), id("registration_kept"));
                Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse("registry_fixture:blocked"),
                        new Item(new Item.Properties()));
                Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse("other_fixture:blocked"),
                        new Item(new Item.Properties()));
            }
            event.register(Registries.SOUND_EVENT, id("registration_blocked"), () ->
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id("registration_blocked")));
        }
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void deferredBlocksAndItemsAreActuallyAbsent(GameTestHelper helper) {
        absent(helper, BuiltInRegistries.BLOCK, "registration_blocked");
        absent(helper, BuiltInRegistries.ITEM, "registration_blocked");
        helper.assertTrue(Registration.BLOCKED.get() != null && Registration.BLOCKED_ITEM.get() != null,
                "owning-mod deferred references survive without registering replacement IDs");
        helper.assertTrue(BuiltInRegistries.BLOCK.getResourceKey(Registration.BLOCKED.get()).isEmpty()
                        && BuiltInRegistries.ITEM.getResourceKey(Registration.BLOCKED_ITEM.get()).isEmpty(),
                "blocked values have no registry key or replacement alias");
        helper.assertTrue(Registration.BLOCKED_ITEM.get().getBlock() == Registration.BLOCKED.get(),
                "blocked block-item suppliers can still reference the blocked block");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void directAndPreconstructedRegistrationsDoNotBreakFreeze(GameTestHelper helper) {
        absent(helper, BuiltInRegistries.BLOCK, "registration_preconstructed");
        absent(helper, BuiltInRegistries.BLOCK, "registration_helper");
        absent(helper, BuiltInRegistries.ITEM, "registration_direct");
        helper.assertTrue(Registration.directItem.isBound(), "direct registration returns a usable internal reference");
        helper.assertTrue(BuiltInRegistries.ITEM.getResourceKey(Registration.directItem.value()).isEmpty(),
                "returned direct reference is absent from the registry");
        // Reaching GameTests already requires NeoForge to freeze the real registries successfully.
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void rulesOnlyAffectTheSelectedRegistryAndExactId(GameTestHelper helper) {
        absent(helper, BuiltInRegistries.ITEM, "registration_item_only");
        present(helper, BuiltInRegistries.BLOCK, "registration_item_only");
        absent(helper, BuiltInRegistries.BLOCK, "registration_block_only");
        present(helper, BuiltInRegistries.ITEM, "registration_block_only");
        present(helper, BuiltInRegistries.BLOCK, "registration_kept");
        present(helper, BuiltInRegistries.ITEM, "registration_kept");
        present(helper, BuiltInRegistries.SOUND_EVENT, "registration_blocked");
        helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("registry_fixture:blocked"))
                        && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("other_fixture:blocked")),
                "exact IDs apply to arbitrary namespaces without matching another namespace");
        helper.assertTrue(BuiltInRegistries.BLOCK.getResourceKey(Registration.KEPT.get()).isPresent()
                        && Registration.KEPT.get().asItem() == Registration.KEPT_ITEM.get(),
                "unblocked siblings keep normal callbacks, block-item mapping and deferred holders");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void kubejsCreatedEntriesAreAlsoBlocked(GameTestHelper helper) {
        absent(helper, BuiltInRegistries.ITEM, "registration_script_item");
        absent(helper, BuiltInRegistries.BLOCK, "registration_script_block");
        absent(helper, BuiltInRegistries.ITEM, "registration_script_block");
        present(helper, BuiltInRegistries.ITEM, "registration_script_kept");
        present(helper, BuiltInRegistries.ITEM, "registration_late_kept");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void blockedIdsCannotBecomeRegistryAliases(GameTestHelper helper) {
        absent(helper, BuiltInRegistries.ITEM, "registration_blocked_alias");
        absent(helper, BuiltInRegistries.ITEM, "registration_early_alias");
        helper.assertTrue(BuiltInRegistries.ITEM.getHolder(id("registration_kept_alias")).orElseThrow().value()
                        == Registration.KEPT_ITEM.get(), "unblocked aliases still resolve normally");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void subsequentStartupLoadsCannotChangeRegistrationRules(GameTestHelper helper) {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        net.quiwh.cwi.compat.CwiKubeJSEvents.BLOCK_REGISTRATION.listenJava(
                dev.latvian.mods.kubejs.script.ScriptType.STARTUP, null, event -> {
                    calls.incrementAndGet();
                    ((net.quiwh.cwi.compat.RegistrationBlockEvent) event).preventItem("cwi:registration_kept");
                    return null;
                });
        new net.quiwh.cwi.compat.CwiKubeJSPlugin().afterScriptsLoaded(
                dev.latvian.mods.kubejs.KubeJS.getStartupScriptManager());
        helper.assertTrue(calls.get() == 0, "subsequent startup loads do not dispatch another registration event");
        present(helper, BuiltInRegistries.ITEM, "registration_kept");
        absent(helper, BuiltInRegistries.ITEM, "registration_blocked");
        helper.succeed();
    }

    private static void absent(GameTestHelper helper, Registry<?> registry, String path) {
        var id = id(path);
        helper.assertTrue(!registry.containsKey(id) && !registry.keySet().contains(id)
                        && registry.getHolder(id).isEmpty(),
                registry.key().location() + " must not contain " + id + " in keys or holders");
    }
    private static void present(GameTestHelper helper, Registry<?> registry, String path) {
        helper.assertTrue(registry.containsKey(id(path)), "unblocked entry remains: " + path);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(CwiCore.ID, path); }
}
