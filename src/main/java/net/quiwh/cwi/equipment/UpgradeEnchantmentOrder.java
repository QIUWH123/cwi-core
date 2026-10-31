package net.quiwh.cwi.equipment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** The 1.21 enchantment component is unordered; upgrade wear needs a separate ordered history. */
final class UpgradeEnchantmentOrder {
    private static final String KEY = "cwi:enchantment_order";

    static List<String> read(ItemStack stack, ItemEnchantments enchantments) {
        Set<String> current = new TreeSet<>();
        enchantments.keySet().forEach(holder -> current.add(holder.unwrapKey().orElseThrow().location().toString()));
        Set<String> ordered = new LinkedHashSet<>();
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (Tag value : data.getList(KEY, Tag.TAG_STRING)) {
            if (current.contains(value.getAsString())) ordered.add(value.getAsString());
        }
        // Honor an original list retained by an external item migration, when available.
        for (Tag value : data.getList("Enchantments", Tag.TAG_COMPOUND)) {
            String id = ((net.minecraft.nbt.CompoundTag)value).getString("id");
            if (current.contains(id)) ordered.add(id);
        }
        // Existing items without history cannot reveal their old list order. Initialize once,
        // deterministically; new enchantments are appended, and removed entries are discarded.
        ordered.addAll(current);
        return new ArrayList<>(ordered);
    }

    static void write(ItemStack stack, List<String> order) {
        ListTag saved = new ListTag();
        order.forEach(id -> saved.add(StringTag.valueOf(id)));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(KEY, saved));
    }

    private UpgradeEnchantmentOrder() {}
}
