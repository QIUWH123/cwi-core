package net.quiwh.cwi.compat;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/** Shared group initialized before KubeJS exposes its handlers to scripts. */
public final class CwiKubeJSEvents {
    public static final EventGroup GROUP = EventGroup.of("CwiEvents");
    public static final EventHandler EQUIPMENT_UPGRADES = GROUP.server("equipmentUpgrades", () -> EquipmentUpgradeEvent.class);
    public static final EventHandler BLOCK_REGISTRATION = GROUP.startup("blockRegistration", () -> RegistrationBlockEvent.class);
    public static final EventHandler SHADOW_DROP = GROUP.client("shadowDrop", () -> ShadowDropEvent.class);

    public static final EventHandler GASES = GROUP.startup("gases", () -> GasConfigurationEvent.class);

    private CwiKubeJSEvents() {}
}
