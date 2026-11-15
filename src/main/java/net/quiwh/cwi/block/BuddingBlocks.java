package net.quiwh.cwi.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

import java.util.Map;

/** Native registrations for the two infected budding blocks from the pack. */
public final class BuddingBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CwiCore.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CwiCore.ID);

    private static final Map<ResourceLocation, ResourceLocation> AMETHYST_GROWTH = Map.of(
            id("minecraft:air"), id("minecraft:small_amethyst_bud"),
            id("minecraft:small_amethyst_bud"), id("minecraft:medium_amethyst_bud"),
            id("minecraft:medium_amethyst_bud"), id("minecraft:large_amethyst_bud"),
            id("minecraft:large_amethyst_bud"), id("minecraft:amethyst_cluster"));
    private static final Map<ResourceLocation, ResourceLocation> QUARTZ_GROWTH = Map.of(
            id("minecraft:air"), id("cwi:small_quartz_bud"),
            id("cwi:small_quartz_bud"), id("cwi:medium_quartz_bud"),
            id("cwi:medium_quartz_bud"), id("cwi:large_quartz_bud"),
            id("cwi:large_quartz_bud"), id("cwi:quartz_crystal"));

    public static final DeferredHolder<Block, InfectedBuddingBlock> INFECTED_BUDDING_AMETHYST = BLOCKS.register(
            "infected_budding_amethyst", () -> create(AMETHYST_GROWTH));
    public static final DeferredHolder<Block, InfectedBuddingBlock> INFECTED_BUDDING_QUARTZ = BLOCKS.register(
            "infected_budding_quartz", () -> create(QUARTZ_GROWTH));

    public static final DeferredHolder<Item, BlockItem> INFECTED_BUDDING_AMETHYST_ITEM = ITEMS.register(
            "infected_budding_amethyst", () -> new BlockItem(INFECTED_BUDDING_AMETHYST.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> INFECTED_BUDDING_QUARTZ_ITEM = ITEMS.register(
            "infected_budding_quartz", () -> new BlockItem(INFECTED_BUDDING_QUARTZ.get(), new Item.Properties()));

    private static InfectedBuddingBlock create(Map<ResourceLocation, ResourceLocation> growthStages) {
        return new InfectedBuddingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                .sound(SoundType.AMETHYST)
                .randomTicks()
                .requiresCorrectToolForDrops(), growthStages);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private BuddingBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
