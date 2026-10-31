package net.quiwh.cwi.compat;

import dev.latvian.mods.kubejs.core.EntityKJS;
import net.minecraft.world.entity.player.Player;

/** Optional one-time migration of KubeJS's separately persisted exposure values. */
public final class LegacyInteractionData {
    public static void copyToxin(Player player, String key) {
        var old = ((EntityKJS) player).kjs$getPersistentData();
        double value = old.getDouble(key);
        if (old.contains(key) && Double.isFinite(value) && value >= 0) player.getPersistentData().putDouble(key, value);
    }
    private LegacyInteractionData() {}
}
