package net.quiwh.cwi;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.negodya1.vintageimprovements.VintageItems;
import com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeBlockEntity;
import com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeStructuralBlockEntity;
import com.negodya1.vintageimprovements.content.kinetics.curving_press.CurvingPressBlockEntity;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;

/** Exercises native machines, old saves, destruction and real comparator notifications. */
@PrefixGameTestTemplate(false)
public final class VintageRedstoneGameTests {
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("vintageimprovements")) event.register(VintageRedstoneGameTests.class);
        }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("vintageimprovements", path); }
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T place(GameTestHelper helper, String path) {
        helper.setBlock(CENTER, BuiltInRegistries.BLOCK.get(id(path)));
        return (T) helper.getBlockEntity(CENTER);
    }
    private static CentrifugeBlockEntity centrifuge(GameTestHelper helper) {
        CentrifugeBlockEntity be = place(helper, "centrifuge");
        for (int i = 0; i < 4; i++) be.addBasin(AllBlocks.BASIN.asStack());
        return be;
    }
    private static int signal(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel(); var absolute = helper.absolutePos(pos); var state = level.getBlockState(absolute);
        return state.getAnalogOutputSignal(level, absolute);
    }
    private static void comparator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
    }
    private static void powered(GameTestHelper helper, BlockPos pos, boolean expected) {
        helper.assertTrue(helper.getBlockState(pos).getValue(ComparatorBlock.POWERED) == expected,
                "physical comparator must become " + (expected ? "powered" : "unpowered"));
    }
    private static void forceLegacyFlag(BlockEntity be, String field) {
        try { var f = be.getClass().getDeclaredField(field); f.setAccessible(true); f.setBoolean(be, true); }
        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void moduleItemsAndTheirRecipesAreRemoved(GameTestHelper helper) {
        for (String path : List.of("redstone_module", "incomplete_redstone_module")) {
            helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(id(path)) && BuiltInRegistries.ITEM.getHolder(id(path)).isEmpty(), "module absent: " + path);
        }
        for (String directory : List.of("recipe", "advancement")) {
            var definitions = new HashMap<ResourceLocation, JsonElement>();
            SimpleJsonResourceReloadListener.scanDirectory(helper.getLevel().getServer().getResourceManager(), directory, new Gson(), definitions);
            for (var entry : definitions.entrySet()) for (String path : List.of("redstone_module", "incomplete_redstone_module"))
                helper.assertTrue(!entry.getValue().toString().contains("\"vintageimprovements:" + path + "\""), "removed module reference: " + entry.getKey());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void centrifugeRetainsNativeRecipeSignalWithoutModule(GameTestHelper helper) {
        var be = centrifuge(helper);
        helper.assertTrue(signal(helper, CENTER) == 0, "empty centrifuge has no recipe");
        be.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        helper.assertTrue(be.isProccesingNow(), "fixture has an eligible recipe");
        helper.assertTrue(signal(helper, CENTER) == 15, "recipe signal works without installing a module or spinning");
        be.outputInv.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.assertTrue(signal(helper, CENTER) == 0, "full output still suppresses native signal");
        be.outputInv.clearContent();
        helper.assertTrue(signal(helper, CENTER) == 15, "extraction restores recipe signal");
        be.inputInv.clearContent();
        helper.assertTrue(signal(helper, CENTER) == 0, "removal clears signal");
        helper.assertTrue(!be.addRedstoneApp(VintageItems.REDSTONE_MODULE.asStack()), "installation is obsolete");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID, timeoutTicks = 100)
    public static void centrifugeUpdatesStructuralComparatorAutomatically(GameTestHelper helper) {
        var be = centrifuge(helper); var output = new BlockPos(0, 1, 2);
        comparator(helper, output);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 1, 2)) instanceof CentrifugeStructuralBlockEntity, "native multiblock formed");
            be.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        });
        helper.runAtTickTime(15, () -> { powered(helper, output, true); be.inputInv.clearContent(); });
        helper.runAtTickTime(25, () -> { powered(helper, output, false); helper.succeed(); });
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void curvingPressRetainsNativeHeadSignalWithoutModule(GameTestHelper helper) {
        CurvingPressBlockEntity be = place(helper, "curving_press");
        helper.assertTrue(signal(helper, CENTER) == 0, "headless press is unpowered");
        for (int mode = 1; mode <= 5; mode++) {
            be.mode = mode;
            helper.assertTrue(signal(helper, CENTER) == 15, "head mode " + mode + " powers comparator");
        }
        be.mode = 1; be.durability = 1;
        be.onItemPressed(ItemStack.EMPTY, 1);
        helper.assertTrue(signal(helper, CENTER) == 0, "worn-out head clears signal");
        helper.assertTrue(!be.addRedstoneApp(VintageItems.REDSTONE_MODULE.asStack()), "installation is obsolete");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID, timeoutTicks = 100)
    public static void curvingPressNotifiesComparatorForHeadChanges(GameTestHelper helper) {
        CurvingPressBlockEntity be = place(helper, "curving_press"); var output = CENTER.west();
        comparator(helper, output);
        helper.runAtTickTime(5, () -> { be.mode = 1; be.durability = 1; be.setChanged(); });
        helper.runAtTickTime(15, () -> { powered(helper, output, true); be.onItemPressed(ItemStack.EMPTY, 1); });
        helper.runAtTickTime(25, () -> { powered(helper, output, false); helper.succeed(); });
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void oldCentrifugeSavesKeepFunctionWithoutAttachment(GameTestHelper helper) {
        var be = centrifuge(helper); be.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        for (Boolean flag : new Boolean[]{null, false, true}) {
            var data = be.saveWithoutMetadata(helper.getLevel().registryAccess());
            if (flag == null) data.remove("RedstoneApp"); else data.putBoolean("RedstoneApp", flag);
            be.loadWithComponents(data, helper.getLevel().registryAccess());
            helper.assertTrue(signal(helper, CENTER) == 15, "legacy centrifuge works with flag=" + flag);
            helper.assertTrue(!be.saveWithoutMetadata(helper.getLevel().registryAccess()).getBoolean("RedstoneApp"), "no installed attachment is saved");
        }
        forceLegacyFlag(be, "redstoneApp");
        helper.assertTrue(!be.saveWithoutMetadata(helper.getLevel().registryAccess()).getBoolean("RedstoneApp"), "externally set legacy flag is not saved");
        be.destroy();
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(1));
        helper.assertTrue(drops.stream().noneMatch(e -> e.getItem().getItem() == VintageItems.REDSTONE_MODULE.get()), "legacy module is never dropped");
        helper.assertTrue(drops.stream().filter(e -> e.getItem().is(AllBlocks.BASIN.asItem())).mapToInt(e -> e.getItem().getCount()).sum() == 4, "four installed basins still drop");
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.PAPER)), "inventory still drops");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void oldPressSavesKeepHeadWithoutAttachment(GameTestHelper helper) {
        CurvingPressBlockEntity be = place(helper, "curving_press"); be.mode = 2; be.durability = 700;
        for (Boolean flag : new Boolean[]{null, false, true}) {
            var data = be.saveWithoutMetadata(helper.getLevel().registryAccess());
            if (flag == null) data.remove("Redstone"); else data.putBoolean("Redstone", flag);
            be.loadWithComponents(data, helper.getLevel().registryAccess());
            helper.assertTrue(signal(helper, CENTER) == 15 && be.mode == 2 && be.durability == 700, "legacy press preserves head with flag=" + flag);
            helper.assertTrue(!be.saveWithoutMetadata(helper.getLevel().registryAccess()).getBoolean("Redstone"), "no installed attachment is saved");
        }
        forceLegacyFlag(be, "redstoneModule");
        helper.assertTrue(!be.saveWithoutMetadata(helper.getLevel().registryAccess()).getBoolean("Redstone"), "externally set legacy flag is not saved");
        be.destroy();
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(1));
        helper.assertTrue(drops.stream().noneMatch(e -> e.getItem().getItem() == VintageItems.REDSTONE_MODULE.get()), "legacy module is never dropped");
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(VintageItems.CONCAVE_CURVING_HEAD.get()) && e.getItem().getDamageValue() == 300), "head and wear still drop");
        helper.succeed();
    }
}
