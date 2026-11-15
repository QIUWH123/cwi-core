package net.quiwh.cwi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class CrystalBuildersGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void unprefixedBuildersRegisterNativeBlocks(GameTestHelper helper) {
        helper.assertTrue(block("source").defaultBlockState().isRandomlyTicking(),
                "Unprefixed budding builder registers a randomly ticking source");
        helper.assertTrue(block("cluster") instanceof AmethystClusterBlock,
                "Unprefixed crystal_cluster uses native cluster behavior");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void defaultsMatchVanillaGrowthAndRandomConsumption(GameTestHelper helper) {
        var level = helper.getLevel();
        var nativePos = helper.absolutePos(new BlockPos(3, 3, 3));
        var customPos = helper.absolutePos(new BlockPos(9, 3, 3));
        var vanillaStages = new Block[]{Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
                Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER};
        for (int seed = 0; seed < 192; seed++) {
            level.setBlock(nativePos, Blocks.BUDDING_AMETHYST.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(customPos, block("source").defaultBlockState(), Block.UPDATE_ALL);
            for (Direction face : Direction.values()) {
                int scenario = (seed + face.ordinal()) % 8;
                BlockState state = switch (scenario) {
                    case 0 -> Blocks.AIR.defaultBlockState();
                    case 1 -> Blocks.WATER.defaultBlockState();
                    case 2, 3, 4, 5 -> vanillaStages[scenario - 2].defaultBlockState()
                            .setValue(AmethystClusterBlock.FACING, face)
                            .setValue(AmethystClusterBlock.WATERLOGGED, seed % 2 == 0);
                    case 6 -> Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
                            .setValue(AmethystClusterBlock.FACING, face.getOpposite());
                    default -> Blocks.STONE.defaultBlockState();
                };
                level.setBlock(nativePos.relative(face), state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
                level.setBlock(customPos.relative(face), state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
            }
            var nativeRandom = RandomSource.create(seed);
            var customRandom = RandomSource.create(seed);
            level.getBlockState(nativePos).randomTick(level, nativePos, nativeRandom);
            level.getBlockState(customPos).randomTick(level, customPos, customRandom);
            for (Direction face : Direction.values()) {
                helper.assertTrue(level.getBlockState(nativePos.relative(face)) == level.getBlockState(customPos.relative(face)),
                        "Default growth matches vanilla: seed " + seed + ", face " + face);
            }
            helper.assertTrue(nativeRandom.nextLong() == customRandom.nextLong(),
                    "Default growth consumes vanilla random calls: seed " + seed);
        }
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void configuredFamilyProgressesOneFaceWithoutCrossFamilyGrowth(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 4, 4));
        level.setBlockAndUpdate(pos, block("up_source").defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        for (String stage : new String[]{"small", "medium", "large", "cluster"}) {
            level.getBlockState(pos).randomTick(level, pos, RandomSource.create(2));
            var grown = level.getBlockState(pos.above());
            helper.assertTrue(grown.is(block(stage)) && grown.getValue(AmethystClusterBlock.FACING) == Direction.UP,
                    "Configured stage grows facing outwards: " + stage);
            for (Direction face : Direction.values()) {
                if (face != Direction.UP) helper.assertTrue(level.getBlockState(pos.relative(face)).isAir(),
                        "Restricted growth does not change " + face);
            }
        }
        var mature = level.getBlockState(pos.above());
        level.getBlockState(pos).randomTick(level, pos, RandomSource.create(2));
        helper.assertTrue(level.getBlockState(pos.above()) == mature, "Mature stage stops growing");
        for (var wrong : new BlockState[]{Blocks.SMALL_AMETHYST_BUD.defaultBlockState(),
                block("small").defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN)}) {
            level.setBlock(pos.above(), wrong, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
            level.getBlockState(pos).randomTick(level, pos, RandomSource.create(2));
            helper.assertTrue(level.getBlockState(pos.above()) == wrong,
                    "Other family or inward-facing bud does not advance");
        }
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void waterPolicyRetainsNativeWaterloggingAndRejectsFlowingWater(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 4, 4));
        level.setBlockAndUpdate(pos, block("up_source").defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.WATER.defaultBlockState());
        for (String stage : new String[]{"small", "medium", "large", "cluster"}) {
            level.getBlockState(pos).randomTick(level, pos, RandomSource.create(3));
            var grown = level.getBlockState(pos.above());
            helper.assertTrue(grown.is(block(stage)) && grown.getValue(AmethystClusterBlock.WATERLOGGED)
                            && grown.getFluidState().getType() == Fluids.WATER,
                    "Water survives each growth transition: " + stage);
        }
        var flowing = Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1);
        level.setBlock(pos.above(), flowing, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
        level.getBlockState(pos).randomTick(level, pos, RandomSource.create(3));
        helper.assertTrue(level.getBlockState(pos.above()) == flowing, "Flowing water cannot start crystal growth");
        level.setBlockAndUpdate(pos, block("dry_source").defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.WATER.defaultBlockState());
        level.getBlockState(pos).randomTick(level, pos, RandomSource.create(3));
        helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.WATER), "growthInWater(false) rejects source water");
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        level.getBlockState(pos).randomTick(level, pos, RandomSource.create(3));
        helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.SMALL_AMETHYST_BUD), "Dry-only source still grows in air");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void zeroChanceNeverGrowsOrConsumesRandom(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 4, 4));
        level.setBlockAndUpdate(pos, block("zero_source").defaultBlockState());
        var actual = RandomSource.create(19);
        var expected = RandomSource.create(19);
        for (int attempt = 0; attempt < 128; attempt++) level.getBlockState(pos).randomTick(level, pos, actual);
        for (Direction face : Direction.values()) helper.assertTrue(level.getBlockState(pos.relative(face)).isAir(),
                "Zero chance leaves every face unchanged");
        helper.assertTrue(actual.nextLong() == expected.nextLong(), "Disabled growth does not draw randomness");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void clusterStagesRetainVanillaShapesLightAndPlacementSupport(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(5, 5, 5));
        var names = new String[]{"small", "medium", "large", "cluster"};
        var vanilla = new Block[]{Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
                Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER};
        var lights = new int[]{1, 2, 4, 5};
        for (int i = 0; i < names.length; i++) {
            var cluster = block(names[i]);
            helper.assertTrue(cluster.defaultBlockState().getLightEmission() == lights[i], "Native light for " + names[i]);
            helper.assertTrue(cluster.defaultBlockState().getSoundType() == vanilla[i].defaultBlockState().getSoundType(),
                    "Native sound for " + names[i]);
            helper.assertTrue(!cluster.defaultBlockState().canOcclude(), "Cluster does not occlude adjacent blocks");
            helper.assertTrue(cluster.defaultBlockState().getPistonPushReaction() == vanilla[i].defaultBlockState().getPistonPushReaction(),
                    "Native piston reaction for " + names[i]);
            for (Direction face : Direction.values()) {
                var actual = cluster.defaultBlockState().setValue(AmethystClusterBlock.FACING, face);
                var reference = vanilla[i].defaultBlockState().setValue(AmethystClusterBlock.FACING, face);
                helper.assertTrue(!Shapes.joinIsNotEmpty(actual.getShape(level, pos), reference.getShape(level, pos), BooleanOp.NOT_SAME),
                        "Native geometry for " + names[i] + " facing " + face);
                var supportPos = pos.relative(face.getOpposite());
                level.setBlockAndUpdate(supportPos, Blocks.STONE.defaultBlockState());
                helper.assertTrue(actual.canSurvive(level, pos), "Supported crystal survives facing " + face);
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
                var context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(cluster.asItem()),
                        new BlockHitResult(Vec3.atCenterOf(supportPos), face, supportPos, false));
                var placed = cluster.getStateForPlacement(context);
                helper.assertTrue(placed != null && placed.getValue(AmethystClusterBlock.FACING) == face
                                && placed.getValue(AmethystClusterBlock.WATERLOGGED),
                        "Placement uses clicked face and source water: " + face);
                level.setBlockAndUpdate(pos, actual);
                level.setBlockAndUpdate(supportPos, Blocks.AIR.defaultBlockState());
                helper.assertTrue(level.getBlockState(pos).isAir(), "Losing support removes crystal facing " + face);
            }
            var north = cluster.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.NORTH);
            helper.assertTrue(north.rotate(Rotation.CLOCKWISE_90).getValue(AmethystClusterBlock.FACING) == Direction.EAST,
                    "Native quarter turn");
            helper.assertTrue(north.mirror(Mirror.LEFT_RIGHT).getValue(AmethystClusterBlock.FACING) == Direction.SOUTH,
                    "Native mirror");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void inheritedBoxAndLightOverridesRemainAvailable(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var custom = block("box");
        helper.assertTrue(custom.defaultBlockState().getLightEmission() == 10, "KubeJS lightLevel override is used");
        helper.assertTrue(block("unlit").defaultBlockState().getLightEmission() == 0, "Explicit zero light stays zero");
        for (Direction face : Direction.values()) {
            AABB expected = switch (face) {
                case UP -> new AABB(.25, 0, .25, .75, .1875, .75);
                case DOWN -> new AABB(.25, .8125, .25, .75, 1, .75);
                case NORTH -> new AABB(.25, .25, .8125, .75, .75, 1);
                case SOUTH -> new AABB(.25, .25, 0, .75, .75, .1875);
                case EAST -> new AABB(0, .25, .25, .1875, .75, .75);
                case WEST -> new AABB(.8125, .25, .25, 1, .75, .75);
            };
            var shape = custom.defaultBlockState().setValue(AmethystClusterBlock.FACING, face).getShape(level, pos);
            helper.assertTrue(shape.bounds().equals(expected), "Inherited UP box rotates to " + face);
        }
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void nativeLootSupportsShardsSilkTouchFortuneAndNoDrops(GameTestHelper helper) {
        var pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var silk = pick.copy();
        silk.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        var fortune = pick.copy();
        fortune.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
        var pos = helper.absolutePos(new BlockPos(4, 4, 4));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (String stage : new String[]{"small", "medium", "large"}) {
            var bud = block(stage);
            helper.assertTrue(Block.getDrops(bud.defaultBlockState(), helper.getLevel(), pos, null, player, pick).isEmpty(),
                    "Immature bud needs Silk Touch: " + stage);
            var drops = Block.getDrops(bud.defaultBlockState(), helper.getLevel(), pos, null, player, silk);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(bud.asItem()) && drops.getFirst().getCount() == 1,
                    "Silk Touch keeps configured bud: " + stage);
        }
        var mature = block("cluster").defaultBlockState();
        var normal = Block.getDrops(mature, helper.getLevel(), pos, null, player, pick);
        helper.assertTrue(normal.size() == 1 && normal.getFirst().is(Items.AMETHYST_SHARD) && normal.getFirst().getCount() == 4,
                "Native mature cluster gives four shards with proper tool");
        var handDrops = Block.getDrops(mature, helper.getLevel(), pos, null, player, ItemStack.EMPTY);
        helper.assertTrue(handDrops.size() == 1 && handDrops.getFirst().is(Items.AMETHYST_SHARD) && handDrops.getFirst().getCount() == 2,
                "Native mature cluster gives two shards without preferred harvest tool");
        var silkDrops = Block.getDrops(mature, helper.getLevel(), pos, null, player, silk);
        helper.assertTrue(silkDrops.size() == 1 && silkDrops.getFirst().is(block("cluster").asItem()),
                "Silk Touch keeps configured mature cluster");
        int total = 0;
        for (int i = 0; i < 32; i++) {
            var drops = Block.getDrops(mature, helper.getLevel(), pos, null, player, fortune);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Items.AMETHYST_SHARD)
                            && drops.getFirst().getCount() >= 4 && drops.getFirst().getCount() <= 16,
                    "Fortune stays in native shard range");
            total += drops.getFirst().getCount();
        }
        helper.assertTrue(total > 128, "Fortune increases mature shard yield");
        helper.assertTrue(Block.getDrops(block("source").defaultBlockState(), helper.getLevel(), pos, null, player, silk).isEmpty(),
                "Budding sources never drop themselves by default");
        helper.assertTrue(Block.getDrops(block("no_drops").defaultBlockState(), helper.getLevel(), pos, null, player, pick).isEmpty(),
                "Inherited noDrops overrides mature default loot");
        var customDrops = Block.getDrops(block("source_drops").defaultBlockState(), helper.getLevel(), pos, null, player, pick);
        helper.assertTrue(customDrops.size() == 1 && customDrops.getFirst().is(Items.DIAMOND),
                "Inherited drops overrides budding source no-loot default");
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void configuredEffectsRunAfterSuccessfulGrowthOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 4, 4));
        var pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(6, 4, 4));
        level.setBlockAndUpdate(pos, block("effect_source").defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        level.getBlockState(pos).randomTick(level, pos, RandomSource.create(5));
        helper.assertTrue(!pig.hasEffect(MobEffects.DARKNESS), "Blocked growth causes no configured effect");
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        level.getBlockState(pos).randomTick(level, pos, RandomSource.create(5));
        var effect = pig.getEffect(MobEffects.DARKNESS);
        helper.assertTrue(effect != null && effect.getDuration() == 100 && effect.getAmplifier() == 0,
                "Successful growth applies configured tick duration and first level");
        pig.removeEffect(MobEffects.DARKNESS);
        level.setBlockAndUpdate(pos.above(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        level.getBlockState(pos).randomTick(level, pos, RandomSource.create(5));
        helper.assertTrue(!pig.hasEffect(MobEffects.DARKNESS), "Mature cluster causes no repeated effect");
        helper.succeed();
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("cwi:crystal_contract_" + path));
    }
}
