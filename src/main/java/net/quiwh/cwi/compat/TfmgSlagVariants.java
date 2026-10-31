package net.quiwh.cwi.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

/** Restores the decorative slag variants removed by TFMG Community Edition. */
public final class TfmgSlagVariants {
    private static final List<ResourceLocation> VARIANTS = List.of(
            id("slag_bricks_slab"), id("slag_bricks_stairs"), id("slag_bricks_wall"));

    private TfmgSlagVariants() {}

    public static void register(IEventBus bus) {
        if (!ModList.get().isLoaded("tfmg")) return;
        bus.addListener(TfmgSlagVariants::registerEntries);
        bus.addListener(EventPriority.LOWEST, TfmgSlagVariants::addCreativeItems);
    }

    private static void registerEntries(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            Block bricks = BuiltInRegistries.BLOCK.getOptional(id("slag_bricks")).orElseThrow();
            for (ResourceLocation id : VARIANTS) {
                if (BuiltInRegistries.BLOCK.containsKey(id)) continue;
                var properties = BlockBehaviour.Properties.ofFullCopy(bricks);
                Block block = switch (id.getPath()) {
                    case "slag_bricks_slab" -> new SlabBlock(properties);
                    case "slag_bricks_stairs" -> new StairBlock(bricks.defaultBlockState(), properties);
                    case "slag_bricks_wall" -> new WallBlock(properties);
                    default -> throw new IllegalStateException("Unknown slag variant: " + id);
                };
                helper.register(id, block);
            }
        });
        event.register(Registries.ITEM, helper -> {
            for (ResourceLocation id : VARIANTS) {
                if (!BuiltInRegistries.ITEM.containsKey(id)) {
                    helper.register(id, new BlockItem(BuiltInRegistries.BLOCK.get(id), new Item.Properties()));
                }
            }
        });
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(id("tfmg_decoration"))) return;
        for (ResourceLocation id : VARIANTS) {
            var stack = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
            if (!stack.isEmpty() && !event.getParentEntries().contains(stack)) event.accept(stack);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tfmg", path);
    }
}
