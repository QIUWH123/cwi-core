package net.quiwh.cwi;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class MysteriousConversionGameTests {
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void blazeCaptureDisabled(GameTestHelper helper) {
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            var player = helper.makeMockPlayer(mode);
            for (var hand : InteractionHand.values()) {
                var stack = AllItems.EMPTY_BLAZE_BURNER.asStack(2);
                player.setItemInHand(hand, stack);
                var blaze = helper.spawn(EntityType.BLAZE, new BlockPos(3, 2, 3));
                var result = stack.getItem().interactLivingEntity(stack, player, blaze, hand);
                helper.assertTrue(!result.consumesAction() && blaze.isAlive() && stack.getCount() == 2
                        && player.getInventory().countItem(AllBlocks.BLAZE_BURNER.asItem()) == 0,
                        "empty burners cannot capture blazes in either hand or game mode");
                blaze.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void spawnerCaptureDisabledAndBurnersStillPlace(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.SPAWNER);
        SpawnerBlockEntity spawner = helper.getBlockEntity(pos);
        spawner.setEntityId(EntityType.BLAZE, helper.getLevel().random);
        var stack = AllItems.EMPTY_BLAZE_BURNER.asStack(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var abs = helper.absolutePos(pos);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        stack.getItem().useOn(context);
        helper.assertTrue(player.getInventory().countItem(AllBlocks.BLAZE_BURNER.asItem()) == 0
                        && player.getItemInHand(InteractionHand.MAIN_HAND).is(AllItems.EMPTY_BLAZE_BURNER.get())
                        && helper.getBlockState(pos.above()).is(AllBlocks.BLAZE_BURNER.get())
                        && helper.getBlockState(pos.above()).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE,
                "spawners grant no filled burner; the empty cage still places normally");
        helper.setBlock(pos.above(), Blocks.AIR);
        stack = AllBlocks.BLAZE_BURNER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        stack.getItem().useOn(context);
        helper.assertTrue(helper.getBlockEntity(pos.above()) instanceof BlazeBurnerBlockEntity
                        && helper.getBlockState(pos.above()).getValue(BlazeBurnerBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE,
                "crafted filled burners still place with a working block entity");
        var fuel = new ItemStack(net.minecraft.world.item.Items.COAL);
        var fed = BlazeBurnerBlock.tryInsert(helper.getBlockState(pos.above()), helper.getLevel(), abs.above(), fuel, false, false, false);
        BlazeBurnerBlockEntity burner = helper.getBlockEntity(pos.above());
        helper.assertTrue(fed.getResult().consumesAction() && fuel.isEmpty() && burner.getRemainingBurnTime() > 0,
                "filled burners still consume ordinary fuel and produce heat");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    public static final class OptionalConversions {
        @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
        public static void bothChunkLoaderCapturesDisabled(GameTestHelper helper) {
            if (!ModList.get().isLoaded("create_power_loader")) { helper.succeed(); return; }
            for (String material : new String[]{"andesite", "brass"}) {
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create_power_loader:empty_" + material + "_chunk_loader"));
                helper.assertTrue(item != net.minecraft.world.item.Items.AIR, "native empty chunk loader exists");
                for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
                    var player = helper.makeMockPlayer(mode);
                    for (var hand : InteractionHand.values()) {
                        var stack = new ItemStack(item, 2);
                        player.setItemInHand(hand, stack);
                        var ghast = helper.spawn(EntityType.GHAST, new BlockPos(3, 3, 3));
                        var result = item.interactLivingEntity(stack, player, ghast, hand);
                        helper.assertTrue(!result.consumesAction() && ghast.isAlive() && stack.getCount() == 2
                                        && player.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse(
                                                "create_power_loader:" + material + "_chunk_loader"))) == 0,
                                "empty " + material + " loader cannot capture ghasts in either hand or mode");
                        ghast.discard();
                    }
                }
            }
            helper.succeed();
        }

        @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
        public static void lightningCannotCreateMagnets(GameTestHelper helper) {
            if (!ModList.get().isLoaded("powergrid")) { helper.succeed(); return; }
            // Force the native chance to 100%, so suppressing a lucky roll cannot pass by accident.
            var chance = org.patryk3211.powergrid.collections.ModdedConfigs.server().recipes.lightningMagnetizationChance;
            double previous = chance.get();
            chance.set(1.0);
            try {
                var pos = helper.absolutePos(new BlockPos(3, 2, 3));
                var alloy = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), AllItems.ANDESITE_ALLOY.asStack(8));
                helper.getLevel().addFreshEntity(alloy);
                var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
                for (int i = 0; i < 16; i++) alloy.thunderHit(helper.getLevel(), bolt);
                helper.assertTrue(alloy.isAlive() && alloy.getItem().is(AllItems.ANDESITE_ALLOY.get())
                                && alloy.getItem().getCount() == 8
                                && helper.getLevel().getEntitiesOfClass(ItemEntity.class, alloy.getBoundingBox().inflate(2),
                                e -> e.getItem().is(org.patryk3211.powergrid.collections.ModdedItems.MAGNET.get())).isEmpty(),
                        "lightning cannot consume alloy or create magnets even at 100% native chance: alive="
                                + alloy.isAlive() + ", remaining=" + alloy.getItem().getCount());
                alloy.discard();
                var magnet = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(),
                        org.patryk3211.powergrid.collections.ModdedItems.MAGNET.asStack(2));
                helper.getLevel().addFreshEntity(magnet);
                magnet.thunderHit(helper.getLevel(), bolt);
                helper.assertTrue(magnet.isAlive() && magnet.getItem().getCount() == 2,
                        "existing magnets keep their native lightning immunity");
                magnet.discard();
                var cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
                float health = cow.getHealth();
                cow.thunderHit(helper.getLevel(), bolt);
                helper.assertTrue(cow.getHealth() < health, "ordinary entities still receive native lightning damage");
                cow.discard();
            } finally { chance.set(previous); }
            helper.succeed();
        }
    }

    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("create_power_loader") || ModList.get().isLoaded("powergrid"))
                event.register(OptionalConversions.class);
        }
    }
}
