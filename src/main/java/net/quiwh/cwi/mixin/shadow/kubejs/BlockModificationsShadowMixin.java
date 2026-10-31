package net.quiwh.cwi.mixin.shadow.kubejs;

import dev.latvian.mods.kubejs.block.BlockModificationKubeEvent.BlockModifications;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.quiwh.cwi.compat.ShadowConfigurable;
import net.quiwh.cwi.shadow.ShadowDropRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BlockModifications.class, remap = false)
public abstract class BlockModificationsShadowMixin implements ShadowConfigurable<BlockModifications> {
    @Shadow public abstract Block block();
    @Unique private Item cwi$shadowItem() {
        var item = block().asItem();
        if (item == Items.AIR) throw new IllegalArgumentException("GUI shadows require a block with an item");
        return item;
    }
    @Override public ShadowDropRules.Style cwi$getShadowStyle() { return ShadowDropRules.itemStyle(cwi$shadowItem()); }
    @Override public void cwi$setShadowStyle(ShadowDropRules.Style style) { ShadowDropRules.itemStyle(cwi$shadowItem(), style); }
}
