package net.quiwh.cwi.mixin.shadow.kubejs;

import dev.latvian.mods.kubejs.item.ItemModificationKubeEvent.ItemModifications;
import net.minecraft.world.item.Item;
import net.quiwh.cwi.compat.ShadowConfigurable;
import net.quiwh.cwi.shadow.ShadowDropRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ItemModifications.class, remap = false)
public abstract class ItemModificationsShadowMixin implements ShadowConfigurable<ItemModifications> {
    @Shadow public abstract Item item();
    @Override public ShadowDropRules.Style cwi$getShadowStyle() { return ShadowDropRules.itemStyle(item()); }
    @Override public void cwi$setShadowStyle(ShadowDropRules.Style style) { ShadowDropRules.itemStyle(item(), style); }
}
