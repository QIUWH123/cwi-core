package net.quiwh.cwi.machine;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

/** Native support materials; structure tags also accept the original pack's optional IDs. */
public final class FurnaceMaterials {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CwiCore.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CwiCore.ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CwiCore.ID);
    public static final DeferredHolder<Block, Block> FIREPROOF_BRICKS = material("fireproof_bricks", Blocks.BRICKS);
    public static final DeferredHolder<Block, Block> CAST_REINFORCEMENT = material("cast_iron_blast_furnace_reinforcement", Blocks.IRON_BLOCK);
    public static final DeferredHolder<Block, Block> STEEL_REINFORCEMENT = material("steel_blast_furnace_reinforcement", Blocks.IRON_BLOCK);
    public static final DeferredHolder<Block, Block> ERUPTIVE_BEDROCK = material("eruptive_bedrock", Blocks.BEDROCK);
    private static DeferredHolder<Block, Block> material(String name, Block copy) {
        var block = BLOCKS.register(name, () -> new Block(properties(name,copy)));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
    private static BlockBehaviour.Properties properties(String name,Block copy) {
        var properties = BlockBehaviour.Properties.ofFullCopy(copy);
        if (name.startsWith("cast_iron")) return properties.strength(10,40).requiresCorrectToolForDrops();
        if (name.startsWith("steel")) return properties.strength(12,48).requiresCorrectToolForDrops();
        if (name.equals("eruptive_bedrock")) return properties.strength(-1,1000).lightLevel(state -> 1);
        return properties;
    }
    static {
        TABS.register("machine_materials", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.cwi.machine_materials"))
                .icon(() -> MachineBlocks.BLAST_FURNACE.get().asItem().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    BLOCKS.getEntries().forEach(entry -> output.accept(entry.get()));
                }).build());
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus); }
    private FurnaceMaterials() {}
}
