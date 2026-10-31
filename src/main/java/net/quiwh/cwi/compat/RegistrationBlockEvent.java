package net.quiwh.cwi.compat;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.typings.Info;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwi.registry.RegistrationBlocker;

import java.util.LinkedHashSet;
import java.util.Set;

/** Exact-ID registration rules, collected only during the first startup-script load. */
public final class RegistrationBlockEvent implements KubeEvent {
    private final Set<ResourceLocation> blocks = new LinkedHashSet<>();
    private final Set<ResourceLocation> items = new LinkedHashSet<>();
    private boolean open = true;

    private RegistrationBlockEvent() {}

    /** Prevent this ID in both the block and item registries. */
    @Info("Prevent an exact namespaced ID in both block and item registries. Startup only; requires a full restart.")
    public RegistrationBlockEvent prevent(String id) { return add(id, true, true); }
    @Info("Prevent an exact namespaced ID only in the block registry. Its item is unaffected.")
    public RegistrationBlockEvent preventBlock(String id) { return add(id, true, false); }
    @Info("Prevent an exact namespaced ID only in the item registry. Its block is unaffected.")
    public RegistrationBlockEvent preventItem(String id) { return add(id, false, true); }

    private RegistrationBlockEvent add(String id, boolean block, boolean item) {
        if (!open) throw new IllegalStateException("Registration rules can only be added inside CwiEvents.blockRegistration; restart the game to change them");
        if (id == null || !id.contains(":")) throw new IllegalArgumentException("Expected an exact namespaced ID, for example modid:name");
        var key = ResourceLocation.tryParse(id);
        if (key == null || key.getPath().isEmpty() || id.startsWith(":"))
            throw new IllegalArgumentException("Invalid block/item ID: " + id);
        // Validate both sides before accepting either rule. Vanilla and early registrations are already too late.
        if (block && BuiltInRegistries.BLOCK.containsKey(key))
            throw new IllegalArgumentException("Block " + key + " was already registered before this event and cannot be prevented");
        if (item && BuiltInRegistries.ITEM.containsKey(key))
            throw new IllegalArgumentException("Item " + key + " was already registered before this event and cannot be prevented");
        if (block) blocks.add(key);
        if (item) items.add(key);
        return this;
    }

    @HideFromJS public static void postStartup() {
        if (RegistrationBlocker.isConfigured()) {
            if (CwiKubeJSEvents.BLOCK_REGISTRATION.hasListeners())
                ScriptType.STARTUP.console.info("CWI registration rules were already applied. Restart the game to change them.");
            return;
        }
        var event = new RegistrationBlockEvent();
        try {
            CwiKubeJSEvents.BLOCK_REGISTRATION.post(event);
        } finally {
            event.open = false;
        }
        RegistrationBlocker.configure(event.blocks, event.items);
    }
}
