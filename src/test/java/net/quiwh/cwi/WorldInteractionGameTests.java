package net.quiwh.cwi;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.spouting.BlockSpoutingBehaviour;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.interaction.*;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class WorldInteractionGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void worldInteractionTypesAreNative(GameTestHelper helper) {
        for (String path : new String[]{"item_impact", "inventory_hazard", "fluid_hazard", "toxin_exposure",
                "lockpicking", "block_spouting", "budding_catalysis", "drying"}) {
            var id = ResourceLocation.fromNamespaceAndPath("cwi", path);
            helper.assertTrue(BuiltInRegistries.RECIPE_TYPE.containsKey(id), "Missing native interaction type " + id);
            helper.assertTrue(BuiltInRegistries.RECIPE_SERIALIZER.containsKey(id), "Missing native interaction serializer " + id);
        }
        helper.succeed();
    }

    public static List<String> verifyScripts(net.minecraft.world.level.Level level) {
        var fingerprints = new ArrayList<String>();
        for (var kind : InteractionRecipes.Kind.values()) {
            var rules = InteractionRecipes.rules(level, kind).stream().filter(holder -> holder.id().getPath().startsWith("contracts/"))
                    .map(holder -> holder.id() + "=" + holder.value().definition()).toList();
            if (rules.isEmpty()) throw new AssertionError("No scripted " + kind.id() + " rules loaded");
            fingerprints.addAll(rules);
        }
        if (fingerprints.size() != 12) throw new AssertionError("Expected 12 interaction contracts, got " + fingerprints.size());
        if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("kubejs:wet_web"))) {
            long packRules = java.util.Arrays.stream(InteractionRecipes.Kind.values())
                    .flatMap(kind -> InteractionRecipes.rules(level, kind).stream())
                    .filter(holder -> holder.id().getPath().startsWith("world/")).count();
            if (packRules != 152) throw new AssertionError("Expected all 152 migrated pack rules, got " + packRules);
        }
        return List.copyOf(fingerprints);
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void everyScriptRuleSurvivesCodecAndNetwork(GameTestHelper helper) {
        verifyScripts(helper.getLevel());
        for (var kind : InteractionRecipes.Kind.values()) for (var holder : InteractionRecipes.rules(helper.getLevel(), kind)) {
            var recipe = holder.value();
            var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var codec = kind.serializer().codec().codec();
            var json = codec.encodeStart(ops, recipe).getOrThrow();
            var copy = codec.parse(ops, json).getOrThrow();
            helper.assertTrue(recipe.definition().equals(copy.definition()), "JSON roundtrip " + holder.id());
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                kind.serializer().streamCodec().encode(buf, recipe);
                var network = kind.serializer().streamCodec().decode(buf);
                helper.assertTrue(network.definition().equals(recipe.definition()) && buf.readableBytes() == 0,
                        "Network roundtrip " + holder.id());
            } finally { buf.release(); }
        }
        reject(helper, InteractionRecipes.Kind.ITEM_IMPACT, "{\"input\":{\"item\":\"minecraft:paper\"},\"min_height\":-1,\"actions\":[{\"type\":\"discard\"}]}");
        reject(helper, InteractionRecipes.Kind.ITEM_IMPACT, "{\"input\":{\"item\":\"minecraft:paper\"},\"actions\":[{\"type\":\"command\"}]}");
        reject(helper, InteractionRecipes.Kind.ITEM_IMPACT, "{\"input\":{\"item\":\"minecraft:paper\"},\"actions\":[{\"type\":\"explosion\",\"fire\":0}]}");
        reject(helper, InteractionRecipes.Kind.INVENTORY_HAZARD, "{\"input\":{\"item\":\"minecraft:diamond\"},\"interval\":0}");
        reject(helper, InteractionRecipes.Kind.DRYING, "{\"input\":\"minecraft:dirt\",\"output\":\"minecraft:mud\",\"chance\":1.1}");
        reject(helper, InteractionRecipes.Kind.DRYING, "{\"input\":\"minecraft:dirt\",\"output\":\"missing:no_block\"}");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void impactPreservesCountComponentsAndRunsOnlyOnce(GameTestHelper helper) {
        var item = item(helper, Items.PAPER, 7, 8);
        item.getItem().set(DataComponents.CUSTOM_NAME, Component.literal("Fragile"));
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(item));
        item.setPos(item.getX(), item.getY() - 3, item.getZ());
        item.setOnGround(true);
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(item));
        helper.assertTrue(item.getItem().is(Items.BRICK) && item.getItem().getCount() == 7,
                "Landing uses native impact and preserves the whole count");
        helper.assertTrue(Component.literal("Fragile").equals(item.getItem().get(DataComponents.CUSTOM_NAME)), "Custom components preserved");
        item.setItem(new ItemStack(Items.PAPER));
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(item));
        helper.assertTrue(item.getItem().is(Items.PAPER), "A landed entity never retriggers");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void impactSaveLoadAndHighestThreshold(GameTestHelper helper) {
        var original = item(helper, Items.PAPER, 3, 12);
        ItemImpacts.beforeTick(original);
        var saved = original.saveWithoutId(new CompoundTag());
        var loaded = new ItemEntity(EntityType.ITEM, helper.getLevel());
        loaded.load(saved);
        loaded.setPos(loaded.getX(), loaded.getY() - 5, loaded.getZ());
        loaded.setOnGround(true);
        ItemImpacts.afterTick(loaded);
        helper.assertTrue(loaded.isRemoved(), "Saved origin survives unload and higher discard threshold wins");
        var upward = item(helper, Items.PAPER, 1, 4);
        ItemImpacts.beforeTick(upward);
        upward.setPos(upward.getX(), upward.getY() + 5, upward.getZ());
        upward.setOnGround(true);
        ItemImpacts.afterTick(upward);
        helper.assertTrue(!upward.isRemoved() && upward.getItem().is(Items.PAPER), "Upward movement is not falling");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID, timeoutTicks = 100)
    public static void realItemEntityLandingDispatchesTheHook(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        var item = item(helper, Items.PAPER, 5, 5);
        item.setDeltaMovement(0, 0, 0);
        item.setPickUpDelay(1000);
        helper.getLevel().addFreshEntity(item);
        helper.succeedWhen(() -> helper.assertTrue(item.getItem().is(Items.BRICK) && item.getItem().getCount() == 5,
                "Native ticking item fell three blocks and converted once"));
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void inventoryHeatAndToxinAccumulateAndDecay(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        WorldHazards.tick(player, 0);
        helper.assertTrue(player.getHealth() == 19, "Hot inventory deals one native damage");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.GOLDEN_APPLE, 4));
        WorldHazards.tick(player, 10);
        double expected = .85 * (Math.sqrt(40) / 50);
        helper.assertTrue(Math.abs(player.getPersistentData().getDouble("cwiTestToxin") - expected) < 1e-10,
                "Base tag plus additional 9x weight preserves the source formula");
        player.getPersistentData().putDouble("cwiTestToxin", 3);
        player.getInventory().clearContent();
        WorldHazards.tick(player, 20);
        helper.assertTrue(Math.abs(player.getPersistentData().getDouble("cwiTestToxin") - 2.55) < 1e-10,
                "Exposure decays with an empty inventory");
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "Toxin adds configured effect from previous exposure");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void legacyToxinStateMigratesOnce(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ((dev.latvian.mods.kubejs.core.EntityKJS) player).kjs$getPersistentData().putDouble("cwiTestToxin", 4);
        WorldHazards.tick(player, 0);
        helper.assertTrue(Math.abs(player.getPersistentData().getDouble("cwiTestToxin") - 3.4) < 1e-10, "Old KubeJS exposure retained");
        WorldHazards.tick(player, 10);
        helper.assertTrue(Math.abs(player.getPersistentData().getDouble("cwiTestToxin") - 2.89) < 1e-10, "Old state is never reimported");
        var saved = player.saveWithoutId(new CompoundTag());
        var loaded = helper.makeMockPlayer(GameType.SURVIVAL);
        loaded.load(saved);
        WorldHazards.tick(loaded, 20);
        helper.assertTrue(Math.abs(loaded.getPersistentData().getDouble("cwiTestToxin") - 2.4565) < 1e-10,
                "Exposure continues decaying after native player save/load");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void fluidContactUsesNativeHeights(GameTestHelper helper) {
        var pig = EntityType.PIG.create(helper.getLevel());
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER);
        pig.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        pig.tick();
        helper.assertTrue(pig.getFluidTypeHeight(Fluids.WATER.getFluidType()) > 0, "Native water contact established");
        float health = pig.getHealth();
        WorldHazards.tick(pig, 0);
        helper.assertTrue(pig.getHealth() == health - 2 && pig.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                "Fluid rule damages and adds configured effect");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void lockpickingBlocksBothHandsAndPreservesLoot(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        var loot = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("minecraft:chests/simple_dungeon"));
        chest.setLootTable(loot);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var empty = click(player, pos, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(empty);
        helper.assertTrue(empty.isCanceled() && chest.getLootTable().equals(loot), "Locked chest denies opening without generating loot");
        var offhand = click(player, pos, InteractionHand.OFF_HAND);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_SWORD));
        NeoForge.EVENT_BUS.post(offhand);
        helper.assertTrue(offhand.isCanceled() && chest.getPersistentData().getInt("cwi:lock") == 1, "Offhand cannot bypass or roll lock");
        var sword = new ItemStack(Items.GOLDEN_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        var attempt = click(player, pos, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(attempt);
        helper.assertTrue(attempt.isCanceled() && sword.getDamageValue() == 3 && chest.getPersistentData().getInt("cwi:lock") == -1,
                "Guaranteed success wears tool exactly once and saves unlock");
        var open = click(player, pos, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(open);
        helper.assertTrue(!open.isCanceled() && chest.getLootTable().equals(loot), "Next click can open with native loot handling");
        var saved = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getCompound("NeoForgeData").getInt("cwi:lock") == -1, "Unlock persists in native block entity save");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        var loaded = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        var reopened = click(player, pos, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(reopened);
        helper.assertTrue(!reopened.isCanceled() && loaded.getPersistentData().getInt("cwi:lock") == -1
                && loaded.getLootTable().equals(loot), "Restored chest stays unlocked without generating loot");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void lockFailureAndPlayerPlacedContainer(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var placed = click(player, pos, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(placed);
        helper.assertTrue(!placed.isCanceled(), "Ordinary placed containers remain native");
        chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("minecraft:chests/simple_dungeon")));
        var sword = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        NeoForge.EVENT_BUS.post(click(player, pos, InteractionHand.MAIN_HAND));
        helper.assertTrue(chest.getPersistentData().getInt("cwi:lock") == 1 && sword.getDamageValue() == 3,
                "Failed attempt wears the tool and remains locked");
        chest.getPersistentData().putInt("Lock", -1);
        var legacy = click(player, pos, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(legacy);
        helper.assertTrue(!legacy.isCanceled() && chest.getPersistentData().getInt("cwi:lock") == -1, "Old unlocked chests stay unlocked");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void blockSpoutingUsesCreateAndNeverMutatesSimulation(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.DIRT);
        var behaviour = BlockSpoutingBehaviour.get(helper.getLevel(), pos);
        helper.assertTrue(behaviour != null, "Native Create behaviour discovers data recipes");
        var fluid = new FluidStack(Fluids.WATER, 1000);
        helper.assertTrue(behaviour.fillBlock(helper.getLevel(), pos, null, fluid, true) == 1000
                && helper.getLevel().getBlockState(pos).is(Blocks.DIRT) && fluid.getAmount() == 1000, "Simulation consumes no fluid or block");
        helper.assertTrue(behaviour.fillBlock(helper.getLevel(), pos, null, new FluidStack(Fluids.WATER, 999), false) == 0,
                "Insufficient fluid is rejected");
        helper.assertTrue(behaviour.fillBlock(helper.getLevel(), pos, null, fluid, false) == 1000
                && helper.getLevel().getBlockState(pos).is(Blocks.MUD), "Native Create executes configured block transformation");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void realSpoutReservesAndConsumesConfiguredFluid(GameTestHelper helper) {
        var target = new BlockPos(1, 1, 1);
        var spoutPos = target.above(2);
        helper.setBlock(target, Blocks.DIRT);
        helper.setBlock(spoutPos, AllBlocks.SPOUT.getDefaultState());
        var absolute = helper.absolutePos(spoutPos);
        var spout = (SpoutBlockEntity) helper.getLevel().getBlockEntity(absolute);
        spout.initialize();
        var tank = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                absolute, Direction.UP);
        helper.assertTrue(tank != null, "Create exposes the native spout fluid capability");
        tank.fill(new FluidStack(Fluids.WATER, 999), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 25; i++) spout.tick();
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(target)).is(Blocks.DIRT)
                && tank.getFluidInTank(0).getAmount() == 999, "Insufficient fluid neither starts nor falls back to Create's cheaper recipe");
        tank.fill(new FluidStack(Fluids.WATER, 1), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 25; i++) spout.tick();
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(target)).is(Blocks.MUD)
                && tank.getFluidInTank(0).isEmpty(), "Create's timed processing consumes exactly the configured 1000mB");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void buddingAttemptsRetainFacingAndWaterlogging(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BUDDING_AMETHYST);
        for (Direction direction : Direction.values()) helper.getLevel().setBlockAndUpdate(pos.relative(direction), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.WATER.defaultBlockState());
        var fluid = new FluidStack(Fluids.LAVA, 50);
        for (int i = 0; i < 20; i++) helper.assertTrue(WorldSpouting.fill(helper.getLevel(), pos, fluid, true) == 50, "Simulation accepts growth");
        helper.assertTrue(helper.getLevel().getBlockState(pos.above()).is(Blocks.WATER), "Repeated simulation never grows crystals");
        helper.assertTrue(WorldSpouting.fill(helper.getLevel(), pos, fluid, false) == 50, "One pour reserves declared fluid");
        var result = helper.getLevel().getBlockState(pos.above());
        helper.assertTrue(result.is(Blocks.LARGE_AMETHYST_BUD) && result.getValue(BlockStateProperties.FACING) == Direction.UP
                && result.getValue(BlockStateProperties.WATERLOGGED), "Three attempts advance one face three stages and retain water");
        WorldSpouting.fill(helper.getLevel(), pos, fluid, false);
        helper.assertTrue(helper.getLevel().getBlockState(pos.above()).is(Blocks.AMETHYST_CLUSTER)
                && WorldSpouting.fill(helper.getLevel(), pos, fluid, true) == 0, "Full growth consumes no further catalyst");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void dryingBuilderPreservesCardinalShapeAndRotation(GameTestHelper helper) {
        var wet = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("cwi:drying_contract_wet"));
        var dry = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("cwi:drying_contract_dry"));
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var state = wet.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        helper.getLevel().setBlockAndUpdate(pos, state);
        helper.assertTrue(state.isRandomlyTicking() && !state.canOcclude() && state.getShape(helper.getLevel(), pos).max(Direction.Axis.Y) == .125,
                "Native builder retains thin cardinal geometry and render properties");
        state.randomTick(helper.getLevel(), pos, RandomSource.create(1));
        var result = helper.getLevel().getBlockState(pos);
        helper.assertTrue(result.is(dry) && result.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
                "Native drying preserves orientation");
        result.randomTick(helper.getLevel(), pos, RandomSource.create(1));
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(dry), "Zero chance never dries");
        helper.succeed();
    }

    private static ItemEntity item(GameTestHelper helper, net.minecraft.world.item.Item type, int count, int height) {
        var pos = helper.absolutePos(new BlockPos(1, height, 1));
        return new ItemEntity(helper.getLevel(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, new ItemStack(type, count));
    }
    private static PlayerInteractEvent.RightClickBlock click(net.minecraft.world.entity.player.Player player, BlockPos pos, InteractionHand hand) {
        return new PlayerInteractEvent.RightClickBlock(player, hand, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    private static void reject(GameTestHelper helper, InteractionRecipes.Kind kind, String json) {
        helper.assertTrue(kind.serializer().codec().codec().parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(json)).isError(), "Invalid rule accepted: " + json);
    }
}
