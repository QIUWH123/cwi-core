package net.quiwh.cwi;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class PetrifiedWoodGameTests {
    private static final List<String> FAMILY = List.of("petrified_boards", "petrified_bookshelf", "petrified_button",
            "petrified_door", "petrified_fence", "petrified_fence_gate", "petrified_hanging_sign", "petrified_log",
            "petrified_planks", "petrified_post", "petrified_pressure_plate", "petrified_roots", "petrified_roots_plant",
            "petrified_sign", "petrified_slab", "petrified_stairs", "petrified_trapdoor", "petrified_vertical_slab",
            "petrified_wall_hanging_sign", "petrified_wall_sign", "petrified_wood", "porous_petrified_log",
            "stripped_petrified_log", "stripped_petrified_post", "stripped_petrified_wood", "trimmed_petrified_planks",
            "vertical_petrified_planks", "reinforced_rope", "amber_cluster", "amber_block");

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void completeFamilyAndItems(GameTestHelper h) {
        for (String name : FAMILY) {
            h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id(name)), "Missing block cwi:" + name);
            if (!name.endsWith("_plant") && !name.contains("wall_"))
                h.assertTrue(block(name).asItem() != Items.AIR, "Missing item cwi:" + name);
        }
        for (String name : List.of("amber", "petrified_boat", "petrified_chest_boat"))
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(id(name)), "Missing item " + name);
        h.assertTrue(block("petrified_log").defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE), "Logs support axes");
        h.assertTrue(new ItemStack(Items.IRON_AXE).isCorrectToolForDrops(block("vertical_petrified_planks").defaultBlockState()), "Vertical planks remain harvestable");
        h.assertTrue(block("reinforced_rope").defaultBlockState().is(BlockTags.CLIMBABLE), "Rope is climbable");
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void strippingPreservesAxisAndPostConnections(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var context = new UseOnContext(h.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        for (String name : List.of("petrified_log", "petrified_wood", "petrified_post")) {
            var state = block(name).defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            var stripped = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, true);
            h.assertTrue(stripped != null && stripped.is(block("stripped_" + name)), name + " strips correctly");
            h.assertTrue(stripped.getValue(BlockStateProperties.AXIS) == Direction.Axis.X, name + " retains axis");
        }
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void ropePartsWaterAndSupport(GameTestHelper h) {
        Block rope = block("reinforced_rope");
        h.setBlock(2, 5, 2, Blocks.STONE);
        for (int y = 4; y >= 2; y--) h.setBlock(2, y, 2, rope);
        h.assertTrue(value(h.getBlockState(new BlockPos(2, 4, 2)), "part").equals("top"), "Rope hook at top");
        h.assertTrue(value(h.getBlockState(new BlockPos(2, 3, 2)), "part").equals("middle"), "Rope middle");
        h.assertTrue(value(h.getBlockState(new BlockPos(2, 2, 2)), "part").equals("bottom"), "Rope knot at bottom");
        var p = h.absolutePos(new BlockPos(2, 3, 2));
        h.assertTrue(rope.isLadder(h.getLevel().getBlockState(p), h.getLevel(), p,
                h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL)), "Rope is a ladder");
        var wet = rope.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        h.assertTrue(wet.getFluidState().getType() == Fluids.WATER, "Rope waterlogs");
        h.setBlock(2, 5, 2, Blocks.AIR);
        h.assertTrue(h.getBlockState(new BlockPos(2, 2, 2)).isAir(), "Removing support removes hanging rope");
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void ropeItemExtendsBelowWetRope(GameTestHelper h) {
        var rope = block("reinforced_rope");
        h.setBlock(2, 5, 2, Blocks.STONE);
        h.setBlock(2, 4, 2, rope);
        h.setBlock(2, 3, 2, rope.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope));
        var clicked = h.absolutePos(new BlockPos(2, 4, 2));
        var context = new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.EAST, clicked, false));
        var item = (net.minecraft.world.item.BlockItem) rope.asItem();
        h.assertTrue(item.updatePlacementContext(context).getClickedPos().equals(clicked.below(2)),
                "Rope extension skips a waterlogged rope and reaches the free end");
        h.assertTrue(item.place(context).consumesAction(), "Side-click places rope at the free end");
        h.assertTrue(h.getLevel().getBlockState(clicked.below(2)).is(rope), "Extended rope is below existing rope");
        h.assertTrue(h.getLevel().getBlockState(clicked.below()).getValue(BlockStateProperties.WATERLOGGED), "Existing wet rope retained");
        h.getLevel().setBlockAndUpdate(clicked.below(2), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(clicked.below(), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        h.assertTrue(item.updatePlacementContext(context).getClickedPos().equals(context.getClickedPos()),
                "A waterlogged solid obstruction stops downward extension");
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void postConnectionsTransformAndStrip(GameTestHelper h) {
        var post = block("petrified_post");
        var chain = net.quiwh.cwi.block.PetrifiedPostBlock.CHAINED;
        var state = post.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X)
                .setValue(BlockStateProperties.WATERLOGGED, true).setValue(chain[Direction.NORTH.ordinal()], true);
        var rotated = state.rotate(Rotation.CLOCKWISE_90);
        h.assertTrue(rotated.getValue(BlockStateProperties.AXIS) == Direction.Axis.Z, "Post axis rotates");
        h.assertTrue(rotated.getValue(chain[Direction.EAST.ordinal()]) && !rotated.getValue(chain[Direction.NORTH.ordinal()]), "Post chains rotate with the beam");
        h.assertTrue(state.mirror(Mirror.LEFT_RIGHT).getValue(chain[Direction.SOUTH.ordinal()]), "Post chains mirror");
        var p = h.absolutePos(new BlockPos(4, 4, 4));
        h.getLevel().setBlockAndUpdate(p, state);
        h.getLevel().setBlockAndUpdate(p.north(), Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        h.getLevel().setBlockAndUpdate(p.below(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        var connected = h.getLevel().getBlockState(p);
        h.assertTrue(connected.getValue(chain[Direction.NORTH.ordinal()]) && connected.getValue(chain[Direction.DOWN.ordinal()]), "Posts connect to chains and hanging lanterns");
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var ctx = new UseOnContext(h.getLevel(), player, InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE),
                new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
        var stripped = connected.getToolModifiedState(ctx, ItemAbilities.AXE_STRIP, true);
        h.assertTrue(stripped != null && stripped.is(block("stripped_petrified_post")), "Post strips");
        for (var property : connected.getProperties())
            h.assertTrue(stripped.getValue(property).equals(connected.getValue(property)), "Stripping preserves " + property.getName());
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void verticalSlabPlacementMergeAndLoot(GameTestHelper h) {
        var slab = (net.quiwh.cwi.block.PetrifiedVerticalSlabBlock) block("petrified_vertical_slab");
        var type = net.quiwh.cwi.block.PetrifiedVerticalSlabBlock.TYPE;
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var p = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
        var stack = new ItemStack(slab, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atLowerCornerOf(p).add(.5, 0, .8), Direction.UP, p.below(), false));
        var item = (net.minecraft.world.item.BlockItem) slab.asItem();
        h.assertTrue(item.place(context).consumesAction(), "Slab places into water");
        var half = h.getLevel().getBlockState(p);
        h.assertTrue(half.is(slab) && half.getValue(BlockStateProperties.WATERLOGGED), "Half slab keeps water");
        var face = half.getValue(type).face;
        h.assertTrue(half.rotate(Rotation.CLOCKWISE_90).getValue(type).face == Rotation.CLOCKWISE_90.rotate(face), "Slab rotates correctly");
        h.assertTrue(half.mirror(Mirror.NONE).equals(half), "No mirror leaves slab unchanged");
        var merge = new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(p), face, p, false));
        h.assertTrue(item.place(merge).consumesAction(), "Second slab merges");
        var full = h.getLevel().getBlockState(p);
        h.assertTrue(full.getValue(type) == net.quiwh.cwi.block.PetrifiedVerticalSlabBlock.Part.DOUBLE
                && !full.getValue(BlockStateProperties.WATERLOGGED), "Double slab removes contained water");
        h.assertTrue(!slab.canPlaceLiquid(player, h.getLevel(), p, full, Fluids.WATER), "Double slabs cannot waterlog");
        var drops = Block.getDrops(full, h.getLevel(), p, null, player, new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertTrue(drops.size() == 1 && drops.getFirst().is(slab.asItem()) && drops.getFirst().getCount() == 2, "Double slab drops both halves");
        for (String name : List.of("petrified_boat", "petrified_chest_boat", "petrified_sign", "petrified_hanging_sign", "reinforced_rope", "petrified_boards"))
            h.assertTrue(h.getLevel().getRecipeManager().byKey(id(name)).isPresent(), "Native recipe decoded: " + name);
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void signsUseNativeBlockEntities(GameTestHelper h) {
        for (String name : List.of("petrified_sign", "petrified_wall_sign", "petrified_hanging_sign", "petrified_wall_hanging_sign")) {
            var state = block(name).defaultBlockState();
            var be = ((EntityBlock) state.getBlock()).newBlockEntity(BlockPos.ZERO, state);
            h.assertTrue(be instanceof SignBlockEntity, name + " has editable sign text");
            h.assertTrue(be.getType().isValid(state), name + " valid block entity type");
            h.assertTrue(be.getType() == (name.contains("hanging") ? BlockEntityType.HANGING_SIGN : BlockEntityType.SIGN), "Native sign persistence");
        }
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void boatsSaveCorrectVariantDropsAndInventory(GameTestHelper h) {
        Boat.Type variant = Boat.Type.byName("cwi:petrified");
        h.assertTrue(variant.getSerializedName().equals("cwi:petrified"), "Petrified boat enum is registered");
        h.assertTrue(variant.getPlanks() == block("petrified_planks"), "Fall drops use petrified planks");
        var boat = new Boat(h.getLevel(), 0, 0, 0);
        boat.setVariant(variant);
        var tag = new CompoundTag(); boat.saveWithoutId(tag);
        var restored = new Boat(h.getLevel(), 0, 0, 0); restored.load(tag);
        h.assertTrue(restored.getVariant() == variant, "Boat variant survives reload");
        h.assertTrue(restored.getDropItem() == BuiltInRegistries.ITEM.get(id("petrified_boat")), "Boat item drop");
        var chest = new ChestBoat(h.getLevel(), 0, 0, 0); chest.setVariant(variant);
        chest.setItem(4, new ItemStack(Items.DIAMOND, 3));
        var chestTag = new CompoundTag(); chest.saveWithoutId(chestTag);
        var loadedChest = new ChestBoat(h.getLevel(), 0, 0, 0); loadedChest.load(chestTag);
        h.assertTrue(loadedChest.getItem(4).is(Items.DIAMOND) && loadedChest.getItem(4).getCount() == 3, "Chest boat keeps inventory");
        h.assertTrue(loadedChest.getDropItem() == BuiltInRegistries.ITEM.get(id("petrified_chest_boat")), "Chest boat drop");
        h.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void porousLogsGrowAmberAndTrimConnects(GameTestHelper h) {
        Block porous = block("porous_petrified_log");
        var p = h.absolutePos(new BlockPos(3, 3, 3));
        h.setBlock(3, 3, 3, porous);
        h.assertTrue(porous.defaultBlockState().isRandomlyTicking(), "Porous logs tick");
        for (int i = 0; i < 2000; i++) porous.defaultBlockState().randomTick(h.getLevel(), p, RandomSource.create(i));
        h.assertTrue(!h.getLevel().getBlockState(p.above()).is(block("amber_cluster")), "No upward amber growth");
        h.assertTrue(Direction.stream().anyMatch(d -> h.getLevel().getBlockState(p.relative(d)).is(block("amber_cluster"))), "Porous logs grow amber");
        var trimmed = block("trimmed_petrified_planks");
        for (int y = 1; y <= 3; y++) h.setBlock(6, y, 6, trimmed);
        h.assertTrue(value(h.getBlockState(new BlockPos(6, 1, 6)), "pillar_state").equals("lower"), "Lower trim");
        h.assertTrue(value(h.getBlockState(new BlockPos(6, 2, 6)), "pillar_state").equals("middle"), "Middle trim");
        h.assertTrue(value(h.getBlockState(new BlockPos(6, 3, 6)), "pillar_state").equals("upper"), "Upper trim");
        h.succeed();
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("cwi", name); }
    private static Block block(String name) { return BuiltInRegistries.BLOCK.get(id(name)); }
    private static String value(BlockState state, String property) {
        var p = state.getBlock().getStateDefinition().getProperty(property);
        if (p == null) return "missing";
        var value = state.getValue(p);
        return value instanceof net.minecraft.util.StringRepresentable s ? s.getSerializedName() : value.toString();
    }
}
