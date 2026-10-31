package net.quiwh.cwi.machine;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.simibubi.create.api.stress.BlockStressValues;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

public final class MachineBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CwiCore.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CwiCore.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CwiCore.ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CwiCore.ID);
    public static final DeferredHolder<Block, BlastFurnaceBlock> BLAST_FURNACE = BLOCKS.register("blast_furnace", () -> new BlastFurnaceBlock(properties().strength(3.5f, 8).sound(SoundType.DEEPSLATE).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 12 : 0)));
    public static final DeferredHolder<Block, FurnaceHatchBlock> FURNACE_HATCH = BLOCKS.register("furnace_hatch", () -> new FurnaceHatchBlock(properties().strength(4, 8).sound(SoundType.DEEPSLATE).forceSolidOn()));
    public static final DeferredHolder<Block, MachineBlock> INCUBATOR = machine("incubator", MachineBlock.Kind.INCUBATOR, MachineBlock.Shaft.DOWN);
    public static final DeferredHolder<Block, MachineBlock> MIXING_VESSEL = machine("mixing_vessel", MachineBlock.Kind.MIXING_VESSEL, MachineBlock.Shaft.VERTICAL);
    public static final DeferredHolder<Block, MachineBlock> STIRLING_ENGINE = machine("stirling_engine", MachineBlock.Kind.STIRLING_ENGINE, MachineBlock.Shaft.FRONT);
    public static final DeferredHolder<Block, MachineBlock> LAVA_EXTRACTOR_HEAD = machine("lava_extractor_head", MachineBlock.Kind.LAVA_EXTRACTOR_HEAD, MachineBlock.Shaft.NONE);
    public static final DeferredHolder<Block, MachineBlock> LAVA_EXTRACTOR_PUMP = machine("lava_extractor_pump", MachineBlock.Kind.LAVA_EXTRACTOR_PUMP, MachineBlock.Shaft.VERTICAL);
    public static final DeferredHolder<Block, MachineBlock> LAVA_EXTRACTOR_STRESS_INPUT = machine("lava_extractor_stress_input", MachineBlock.Kind.LAVA_EXTRACTOR_STRESS_INPUT, MachineBlock.Shaft.VERTICAL);
    public static final DeferredHolder<Block, MachineBlock> LAVA_EXTRACTOR_PUMP_BLADE = machine("lava_extractor_pump_blade", MachineBlock.Kind.LAVA_EXTRACTOR_PUMP_BLADE, MachineBlock.Shaft.VERTICAL);
    public static final DeferredHolder<Block, MachineBlock> SOLAR_EVAPORATOR = machine("solar_evaporator", MachineBlock.Kind.SOLAR_EVAPORATOR, MachineBlock.Shaft.NONE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlastFurnaceBlockEntity>> BLAST_FURNACE_ENTITY = entity("blast_furnace", BlastFurnaceBlockEntity::new, BLAST_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FurnaceHatchBlockEntity>> FURNACE_HATCH_ENTITY = entity("furnace_hatch", FurnaceHatchBlockEntity::new, FURNACE_HATCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncubatorBlockEntity>> INCUBATOR_ENTITY = entity("incubator", IncubatorBlockEntity::new, INCUBATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MixingVesselBlockEntity>> MIXING_VESSEL_ENTITY = entity("mixing_vessel", MixingVesselBlockEntity::new, MIXING_VESSEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StirlingEngineBlockEntity>> STIRLING_ENGINE_ENTITY = entity("stirling_engine", StirlingEngineBlockEntity::new, STIRLING_ENGINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LavaExtractorBlockEntity>> LAVA_EXTRACTOR_HEAD_ENTITY = entity("lava_extractor_head", LavaExtractorBlockEntity::new, LAVA_EXTRACTOR_HEAD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractorKineticBlockEntity>> LAVA_EXTRACTOR_PUMP_ENTITY = entity("lava_extractor_pump", ExtractorKineticBlockEntity::new, LAVA_EXTRACTOR_PUMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractorKineticBlockEntity>> LAVA_EXTRACTOR_STRESS_INPUT_ENTITY = entity("lava_extractor_stress_input", ExtractorKineticBlockEntity::new, LAVA_EXTRACTOR_STRESS_INPUT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractorKineticBlockEntity>> LAVA_EXTRACTOR_PUMP_BLADE_ENTITY = entity("lava_extractor_pump_blade", ExtractorKineticBlockEntity::new, LAVA_EXTRACTOR_PUMP_BLADE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarEvaporatorBlockEntity>> SOLAR_EVAPORATOR_ENTITY = entity("solar_evaporator", SolarEvaporatorBlockEntity::new, SOLAR_EVAPORATOR);

    static {
        for (var holder : BLOCKS.getEntries()) ITEMS.register(holder.getId().getPath(), () -> new BlockItem(holder.get(), new Item.Properties()));
        TABS.register("machines", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.cwi.machines"))
                .icon(() -> INCUBATOR.get().asItem().getDefaultInstance()).displayItems((parameters, output) -> BLOCKS.getEntries().forEach(block -> output.accept(block.get()))).build());
    }
    private static BlockBehaviour.Properties properties() {
        // A collision cube does not make the authored cutout models opaque. This also lets
        // world light reach the block position used to light the internal kinetic partials.
        return BlockBehaviour.Properties.of().strength(1.5f, 6).noOcclusion().isSuffocating((state, level, pos) -> false);
    }
    private static DeferredHolder<Block, MachineBlock> machine(String id, MachineBlock.Kind kind, MachineBlock.Shaft shaft) {
        return BLOCKS.register(id, () -> {
            var properties = properties();
            if (kind == MachineBlock.Kind.INCUBATOR || kind == MachineBlock.Kind.MIXING_VESSEL) properties.strength(2.5f, 6).sound(SoundType.METAL);
            if (kind == MachineBlock.Kind.STIRLING_ENGINE) properties.sound(SoundType.METAL);
            return new MachineBlock(properties, kind, shaft);
        });
    }
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> entity(String id, BlockEntityType.BlockEntitySupplier<T> factory, DeferredHolder<Block, ? extends Block> block) {
        return ENTITIES.register(id, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        bus.addListener(MachineBlocks::registerCapabilities);
        bus.addListener(MachineBlocks::setup);
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Fallbacks permit explicit API registrations and later providers to override defaults.
            BlockStressValues.IMPACTS.registerProvider(block -> block == MIXING_VESSEL.get() ? () -> 8
                    : block == INCUBATOR.get() ? () -> 2 : block == LAVA_EXTRACTOR_PUMP.get() ? () -> 32 : null);
            BlockStressValues.CAPACITIES.registerProvider(block -> block == STIRLING_ENGINE.get() ? () -> 32 : null);
            BlockStressValues.setGeneratorSpeed(128, true).accept(STIRLING_ENGINE.get());
            BlockStressValues.IMPACTS.invalidate();
            BlockStressValues.CAPACITIES.invalidate();
        });
    }
    private static <T extends MachineBlockEntity> void storage(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, MachineBlockEntity::getItemCapability);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, MachineBlockEntity::getFluidCapability);
    }
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        storage(event, BLAST_FURNACE_ENTITY.get()); storage(event, INCUBATOR_ENTITY.get()); storage(event, MIXING_VESSEL_ENTITY.get());
        storage(event, LAVA_EXTRACTOR_HEAD_ENTITY.get()); storage(event, SOLAR_EVAPORATOR_ENTITY.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FURNACE_HATCH_ENTITY.get(), FurnaceHatchBlockEntity::getItemCapability);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FURNACE_HATCH_ENTITY.get(), FurnaceHatchBlockEntity::getFluidCapability);
    }
    private MachineBlocks() {}
}
