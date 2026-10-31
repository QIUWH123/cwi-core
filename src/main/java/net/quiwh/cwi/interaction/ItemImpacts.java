package net.quiwh.cwi.interaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;

/** Per-entity saved state survives chunk unloads without retaining live entity references. */
public final class ItemImpacts {
    private static final String STATE = "cwi:impact";

    public static void beforeTick(ItemEntity item) {
        if (item.level().isClientSide || item.isRemoved()) return;
        CompoundTag root = item.getPersistentData();
        String dimension = item.level().dimension().location().toString();
        if (root.contains(STATE)) {
            var state = root.getCompound(STATE);
            if (dimension.equals(state.getString("dimension"))) return;
        }
        boolean applicable = InteractionRecipes.rules(item.level(), InteractionRecipes.Kind.ITEM_IMPACT).stream()
                .anyMatch(holder -> ((InteractionRules.Impact) holder.value().rule()).input().test(item.getItem()));
        if (!applicable) return;
        var state = new CompoundTag();
        state.putDouble("start_y", item.getY());
        state.putString("dimension", dimension);
        root.put(STATE, state);
    }

    public static void afterTick(ItemEntity item) {
        if (item.level().isClientSide || item.isRemoved() || !item.onGround()) return;
        var state = item.getPersistentData().getCompound(STATE);
        if (state.isEmpty() || state.getBoolean("landed")) return;
        state.putBoolean("landed", true);
        double distance = Math.max(0, state.getDouble("start_y") - item.getY());
        InteractionRules.Impact best = null;
        for (var holder : InteractionRecipes.rules(item.level(), InteractionRecipes.Kind.ITEM_IMPACT)) {
            var rule = (InteractionRules.Impact) holder.value().rule();
            if (rule.minHeight() > distance || !rule.input().test(item.getItem())) continue;
            if (best == null || rule.minHeight() > best.minHeight()) best = rule;
        }
        if (best != null) for (var action : best.actions()) action.apply(item, distance);
    }
    private ItemImpacts() {}
}
