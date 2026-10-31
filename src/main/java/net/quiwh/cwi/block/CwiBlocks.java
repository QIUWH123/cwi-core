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
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.material.MapColor;
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

    public static final DeferredHolder<Block, Block> DEPLETED_DIRT = BLOCKS.register("depleted_dirt", () ->
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.GRAVEL)
                    .strength(0.8f, 0.8f)));
    public static final DeferredHolder<Block, ColoredFallingBlock> ASH_DIRT = BLOCKS.register("ash_dirt", () ->
            new ColoredFallingBlock(new ColorRGBA(0xFF6A4A3B), BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.SAND)
                    .strength(1f, 1f)));
    public static final DeferredHolder<Block, BrushableBlock> SUSPICIOUS_DEPLETED_DIRT = BLOCKS.register(
            "suspicious_depleted_dirt", () -> suspicious(DEPLETED_DIRT));
    public static final DeferredHolder<Block, BrushableBlock> SUSPICIOUS_ASH_DIRT = BLOCKS.register(
            "suspicious_ash_dirt", () -> suspicious(ASH_DIRT));

    public static final DeferredHolder<Item, PlaceOnWaterBlockItem> DRIED_ALGAE_ITEM = ITEMS.register("dride_algae",
            () -> new PlaceOnWaterBlockItem(DRIED_ALGAE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> SPIDER_EGG_ITEM = ITEMS.register("spider_egg",
            () -> new BlockItem(SPIDER_EGG.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> DEPLETED_DIRT_ITEM = ITEMS.register("depleted_dirt",
            () -> new BlockItem(DEPLETED_DIRT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> ASH_DIRT_ITEM = ITEMS.register("ash_dirt",
            () -> new BlockItem(ASH_DIRT.get(), new Item.Properties()));
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
                        output.accept(DEPLETED_DIRT_ITEM.get());
                        output.accept(ASH_DIRT_ITEM.get());
                        output.accept(SUSPICIOUS_DEPLETED_DIRT_ITEM.get());
                        output.accept(SUSPICIOUS_ASH_DIRT_ITEM.get());
                        output.accept(BuddingBlocks.INFECTED_BUDDING_AMETHYST_ITEM.get());
                        output.accept(BuddingBlocks.INFECTED_BUDDING_QUARTZ_ITEM.get());
                        PetrifiedWood.displayItems(output);
                    }).build());

    private CwiBlocks() {}

    private static BrushableBlock suspicious(DeferredHolder<Block, ? extends Block> turnsInto) {
        return new BrushableBlock(turnsInto.get(), SoundType.SUSPICIOUS_GRAVEL.getStepSound(),
                SoundType.ANVIL.getHitSound(), BlockBehaviour.Properties.ofFullCopy(Blocks.SUSPICIOUS_GRAVEL));
    }

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
