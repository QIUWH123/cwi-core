package net.quiwh.cwi.interaction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Tags stay symbolic so the same definition follows tag reloads. */
public record BlockSelector(ResourceLocation id, boolean tag) {
    public static BlockSelector of(String text) {
        boolean tag = text.startsWith("#");
        var id = ResourceLocation.parse(tag ? text.substring(1) : text);
        if (!tag && !BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown block " + id);
        return new BlockSelector(id, tag);
    }
    public boolean test(BlockState state) {
        return tag ? state.is(TagKey.create(Registries.BLOCK, id)) : state.is(BuiltInRegistries.BLOCK.get(id));
    }
    public java.util.List<net.minecraft.world.item.ItemStack> items() {
        return BuiltInRegistries.BLOCK.stream().filter(block -> test(block.defaultBlockState()))
                .map(block -> new net.minecraft.world.item.ItemStack(block)).filter(stack -> !stack.isEmpty()).toList();
    }
}
