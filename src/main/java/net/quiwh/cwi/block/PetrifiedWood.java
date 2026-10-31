package net.quiwh.cwi.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.dispenser.BoatDispenseItemBehavior;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.quiwh.cwi.CwiCore;

/** The complete petrified family, independent of Darker Depths and its optional building mods. */
public final class PetrifiedWood {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CwiCore.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CwiCore.ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, CwiCore.ID);
    private static final Map<String, DeferredHolder<Block, ? extends Block>> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, DeferredHolder<Item, ? extends Item>> ITEM_ENTRIES = new LinkedHashMap<>();
    public static final BlockSetType SET = BlockSetType.register(new BlockSetType("cwi:petrified"));
    public static final WoodType WOOD = WoodType.register(new WoodType("cwi:petrified", SET));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DRIPPING_AMBER = particle("dripping_amber");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FALLING_AMBER = particle("falling_amber");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LANDING_AMBER = particle("landing_amber");

    static {
        add("stripped_petrified_log", () -> new RotatedPillarBlock(log()));
        add("stripped_petrified_wood", () -> new RotatedPillarBlock(log()));
        add("petrified_log", () -> new PetrifiedLogBlock(log(), () -> get("stripped_petrified_log")));
        add("petrified_wood", () -> new PetrifiedLogBlock(log(), () -> get("stripped_petrified_wood")));
        add("petrified_planks", () -> new Block(planks()));
        add("petrified_boards", () -> new RotatedPillarBlock(planks()));
        add("vertical_petrified_planks", () -> new Block(planks()));
        add("trimmed_petrified_planks", () -> new TrimmedPetrifiedPlanksBlock(planks()));
        add("petrified_stairs", () -> new StairBlock(get("petrified_planks").defaultBlockState(), planks()));
        add("petrified_slab", () -> new SlabBlock(planks()));
        add("petrified_vertical_slab", () -> new PetrifiedVerticalSlabBlock(planks()));
        add("petrified_fence", () -> new FenceBlock(planks()));
        add("petrified_fence_gate", () -> new FenceGateBlock(WOOD, planks()));
        add("petrified_door", () -> new DoorBlock(SET, planks().noOcclusion().pushReaction(PushReaction.DESTROY)));
        add("petrified_trapdoor", () -> new TrapDoorBlock(SET, planks().noOcclusion()));
        add("petrified_bookshelf", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF)));
        add("petrified_pressure_plate", () -> new PressurePlateBlock(SET, planks()));
        add("petrified_button", () -> new ButtonBlock(SET, 30, planks()));
        add("stripped_petrified_post", () -> new PetrifiedPostBlock(log().noOcclusion(), null));
        add("petrified_post", () -> new PetrifiedPostBlock(log().noOcclusion(), () -> get("stripped_petrified_post")));
        add("porous_petrified_log", () -> new PorousPetrifiedLogBlock(log().randomTicks().lightLevel(state -> 6)));
        add("petrified_roots", () -> new PetrifiedRootsBlock.Head(roots()), false);
        add("petrified_roots_plant", () -> new PetrifiedRootsBlock.Body(roots()), false);
        item("petrified_roots", () -> new BlockItem(get("petrified_roots"), new Item.Properties()));
        add("petrified_sign", () -> new StandingSignBlock(WOOD, sign()), false);
        add("petrified_wall_sign", () -> new WallSignBlock(WOOD, sign().lootFrom(ENTRIES.get("petrified_sign"))), false);
        add("petrified_hanging_sign", () -> new CeilingHangingSignBlock(WOOD, sign()), false);
        add("petrified_wall_hanging_sign", () -> new WallHangingSignBlock(WOOD, sign().lootFrom(ENTRIES.get("petrified_hanging_sign"))), false);
        item("petrified_sign", () -> new SignItem(new Item.Properties().stacksTo(16), get("petrified_sign"), get("petrified_wall_sign")));
        item("petrified_hanging_sign", () -> new HangingSignItem(get("petrified_hanging_sign"), get("petrified_wall_hanging_sign"), new Item.Properties().stacksTo(16)));
        add("reinforced_rope", () -> new ReinforcedRopeBlock(BlockBehaviour.Properties.of().strength(.1f).sound(SoundType.WOOL).noOcclusion()), false);
        item("reinforced_rope", () -> new ReinforcedRopeItem(get("reinforced_rope"), new Item.Properties()));
        add("amber_cluster", () -> new AmethystClusterBlock(6, 3, BlockBehaviour.Properties.of().strength(1)
                .requiresCorrectToolForDrops().sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(state -> 7).noOcclusion()));
        add("amber_block", () -> new Block(BlockBehaviour.Properties.of().strength(1).requiresCorrectToolForDrops()
                .sound(SoundType.AMETHYST).lightLevel(state -> 7)));
        item("amber", () -> new Item(new Item.Properties()));
        item("petrified_boat", () -> new BoatItem(false, boatType(), new Item.Properties().stacksTo(1)));
        item("petrified_chest_boat", () -> new BoatItem(true, boatType(), new Item.Properties().stacksTo(1)));
    }

    public static Boat.Type boatType() { return PetrifiedBoatType.PETRIFIED.getValue(); }
    public static Block get(String name) {
        var entry = ENTRIES.get(name);
        if (entry == null) throw new IllegalArgumentException("Unknown petrified block: " + name);
        return entry.get();
    }
    public static Item item(String name) { return ITEM_ENTRIES.get(name).get(); }
    public static void displayItems(net.minecraft.world.item.CreativeModeTab.Output output) {
        ITEM_ENTRIES.values().forEach(entry -> output.accept(entry.get()));
    }
    private static void add(String name, Supplier<? extends Block> factory) { add(name, factory, true); }
    private static void add(String name, Supplier<? extends Block> factory, boolean withItem) {
        var entry = BLOCKS.register(name, factory);
        ENTRIES.put(name, entry);
        if (withItem) item(name, () -> new BlockItem(entry.get(), new Item.Properties()));
    }
    private static void item(String name, Supplier<? extends Item> factory) { ITEM_ENTRIES.put(name, ITEMS.register(name, factory)); }
    private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
    private static BlockBehaviour.Properties log() { return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.4f, 3).sound(SoundType.STEM).requiresCorrectToolForDrops(); }
    private static BlockBehaviour.Properties planks() { return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 3).sound(SoundType.WOOD).requiresCorrectToolForDrops(); }
    private static BlockBehaviour.Properties roots() { return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).offsetType(BlockBehaviour.OffsetType.XZ).noCollission().instabreak().sound(SoundType.HANGING_ROOTS); }
    private static BlockBehaviour.Properties sign() { return BlockBehaviour.Properties.of().strength(1).sound(SoundType.WOOD).requiresCorrectToolForDrops().noCollission(); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); PARTICLES.register(bus);
        bus.addListener(PetrifiedWood::signTypes);
        bus.addListener(PetrifiedWood::setup);
    }
    private static void signTypes(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN, get("petrified_sign"), get("petrified_wall_sign"));
        event.modify(BlockEntityType.HANGING_SIGN, get("petrified_hanging_sign"), get("petrified_wall_hanging_sign"));
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(item("petrified_boat"), new BoatDispenseItemBehavior(boatType()));
            DispenserBlock.registerBehavior(item("petrified_chest_boat"), new BoatDispenseItemBehavior(boatType(), true));
        });
    }
    private PetrifiedWood() {}
}
