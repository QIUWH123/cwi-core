package net.quiwh.cwi.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

public final class CwiBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CwiCore.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CwiCore.ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CwiCore.ID);

    // Existing worlds and pack assets use this historical spelling.
    public static final DeferredHolder<Block, DriedAlgaeBlock> DRIED_ALGAE = BLOCKS.register("dride_algae",
            () -> new DriedAlgaeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LILY_PAD).noCollission()));
    public static final DeferredHolder<Block, SpiderEggBlock> SPIDER_EGG = BLOCKS.register("spider_egg",
            () -> new SpiderEggBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TURTLE_EGG)));

    public static final DeferredHolder<Block, BrushableBlock> SUSPICIOUS_DEPLETED_DIRT = BLOCKS.register(
            "suspicious_depleted_dirt", () -> new PackBrushableBlock("kubejs:depleted_dirt", Blocks.DIRT));
    public static final DeferredHolder<Block, BrushableBlock> SUSPICIOUS_ASH_DIRT = BLOCKS.register(
            "suspicious_ash_dirt", () -> new PackBrushableBlock("kubejs:ash_dirt", Blocks.SAND));

    public static final DeferredHolder<Item, PlaceOnWaterBlockItem> DRIED_ALGAE_ITEM = ITEMS.register("dride_algae",
            () -> new PlaceOnWaterBlockItem(DRIED_ALGAE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> SPIDER_EGG_ITEM = ITEMS.register("spider_egg",
            () -> new BlockItem(SPIDER_EGG.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> SUSPICIOUS_DEPLETED_DIRT_ITEM = ITEMS.register(
            "suspicious_depleted_dirt", () -> new BlockItem(SUSPICIOUS_DEPLETED_DIRT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> SUSPICIOUS_ASH_DIRT_ITEM = ITEMS.register(
            "suspicious_ash_dirt", () -> new BlockItem(SUSPICIOUS_ASH_DIRT.get(), new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OTHER = TABS.register("other", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.cwi.other"))
                    .icon(() -> DRIED_ALGAE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(DRIED_ALGAE_ITEM.get());
                        output.accept(SPIDER_EGG_ITEM.get());
                        output.accept(SUSPICIOUS_DEPLETED_DIRT_ITEM.get());
                        output.accept(SUSPICIOUS_ASH_DIRT_ITEM.get());
                        output.accept(BuddingBlocks.INFECTED_BUDDING_AMETHYST_ITEM.get());
                        output.accept(BuddingBlocks.INFECTED_BUDDING_QUARTZ_ITEM.get());
                        PetrifiedWood.displayItems(output);
                        output.accept(net.quiwh.cwi.content.weathered.WeatheredContent.GIRDER.get());
                        output.accept(net.quiwh.cwi.content.weathered.WeatheredContent.BRACKET.get());
                        output.accept(net.quiwh.cwi.content.carriage.CarriageContent.BLOCK.get());
                    }).build());

    private CwiBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener(CwiBlocks::registerBlockEntityValidity);
    }

    private static void registerBlockEntityValidity(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.BRUSHABLE_BLOCK,
                SUSPICIOUS_DEPLETED_DIRT.get(), SUSPICIOUS_ASH_DIRT.get());
    }
}
