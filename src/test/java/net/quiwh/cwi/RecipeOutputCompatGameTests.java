package net.quiwh.cwi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugationRecipe;
import com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeBlockEntity;
import com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingTableBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import io.github.hadron13.rubberworks.blocks.compressor.CompressorBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Registered only in the optional-mod verification run, never required by ordinary core tests. */
@PrefixGameTestTemplate(false)
public final class RecipeOutputCompatGameTests {
    private static final Item[] PRODUCTS = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
            Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI, Items.EMERALD, Items.QUARTZ, Items.CLAY_BALL};

    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("vintageimprovements") && ModList.get().isLoaded("rubberworks"))
                event.register(RecipeOutputCompatGameTests.class);
        }
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void recipesLoadSyncAndEnforceBoundaries(GameTestHelper helper) {
        for (String type : new String[]{"vintageimprovements:vibrating", "vintageimprovements:centrifugation", "rubberworks:compressing"}) {
            int count = type.startsWith("rubberworks") ? 6 : 9;
            var recipe = decode(helper, type, count);
            helper.assertTrue(recipe.validate().isEmpty(), type + " accepts " + count + " results: " + recipe.validate());
            try {
                decode(helper, type, count + 1);
                throw new AssertionError(type + " accepted an extra result");
            } catch (RuntimeException rejected) {
                helper.assertTrue(rejected.getMessage().contains("more item outputs"), "extra result rejected for the expected reason");
            }
            helper.assertTrue(recipe.getRollableResults().size() == count, "native decoding retains every output");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>) recipe.getSerializer();
                serializer.streamCodec().encode(buffer, recipe);
                var copy = (ProcessingRecipe<?, ?>) serializer.streamCodec().decode(buffer);
                helper.assertTrue(copy.getRollableResults().size() == count && buffer.readableBytes() == 0,
                        "recipe synchronization retains every result for " + type);
                helper.assertTrue(copy.getRollableResults().getLast().getChance() == 0.25f,
                        "independent chance survives synchronization");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void vibratingProducesNineAndWaitsForSpace(GameTestHelper helper) {
        VibratingTableBlockEntity machine = place(helper, "vintageimprovements:vibrating_table");
        drive(helper, machine, 64);
        machine.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        for (int i = 0; i < 4; i++) machine.tick();
        helper.assertTrue(!machine.outputInv.isEmpty(), "vibration ran; speed=" + machine.getSpeed() + ", timer=" + machine.timer
                + ", input=" + machine.inputInv.getStackInSlot(0));
        assertProducts(helper, machine.outputInv, 9);
        helper.assertTrue(machine.inputInv.isEmpty(), "one vibration consumes one input");
        machine.outputInv.clearContent();
        fillBlocked(machine.outputInv);
        machine.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        for (int i = 0; i < 4; i++) machine.tick();
        helper.assertTrue(machine.inputInv.getStackInSlot(0).is(Items.PAPER), "blocked vibration preserves input");
        helper.assertTrue(machine.outputInv.getStackInSlot(8).isEmpty(), "blocked vibration produces no partial results");
        machine.outputInv.clearContent();
        for (int i = 0; i < 4; i++) machine.tick();
        assertProducts(helper, machine.outputInv, 9);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void centrifugeProducesNineAndWaitsForSpace(GameTestHelper helper) {
        CentrifugeBlockEntity machine = place(helper, "vintageimprovements:centrifuge");
        for (int i = 0; i < 4; i++) machine.addBasin(AllBlocks.BASIN.asStack());
        var recipe = (CentrifugationRecipe) loaded(helper, "centrifugation");
        machine.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        helper.assertTrue(CentrifugationRecipe.apply(machine, recipe), "centrifuge executes nine-output recipe");
        assertProducts(helper, machine.outputInv, 9);
        helper.assertTrue(machine.inputInv.isEmpty(), "centrifuge consumes one input");
        machine.outputInv.clearContent();
        fillBlocked(machine.outputInv);
        machine.inputInv.setStackInSlot(0, new ItemStack(Items.PAPER));
        helper.assertTrue(!CentrifugationRecipe.apply(machine, recipe), "blocked centrifuge rejects the complete batch");
        helper.assertTrue(machine.inputInv.getStackInSlot(0).is(Items.PAPER), "blocked centrifuge preserves input");
        helper.assertTrue(machine.outputInv.getStackInSlot(8).isEmpty(), "simulation reserves shared output space");
        machine.outputInv.clearContent();
        helper.assertTrue(CentrifugationRecipe.apply(machine, recipe), "centrifuge resumes after extraction");
        assertProducts(helper, machine.outputInv, 9);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void compressorProducesSixPersistsAndWaitsForSpace(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.CAMPFIRE);
        CompressorBlockEntity machine = place(helper, "rubberworks:compressor");
        var motor = drive(helper, machine, 64);
        if (!machine.validSpeed()) motor.generatedSpeed.setValue(-64);
        helper.assertTrue(machine.validSpeed(), "motor drives compressor in its valid direction");
        machine.tank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE);
        machine.recipe = (io.github.hadron13.rubberworks.blocks.compressor.CompressingRecipe) loaded(helper, "compressing");
        for (int i = 0; i < 2; i++) machine.tick();
        helper.assertTrue(!machine.output.isEmpty(), "compression ran; speed=" + machine.getSpeed() + ", timer=" + machine.timer
                + ", heat=" + machine.getHeat() + ", slots=" + machine.output.getSlots());
        assertProducts(helper, machine.output, 6);
        helper.assertTrue(machine.tank.getPrimaryHandler().getFluidAmount() == 100, "one compression consumes 100mB");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = (CompressorBlockEntity) machine.getType().create(machine.getBlockPos(), machine.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
        assertProducts(helper, restored.output, 6);
        var oldSave = saved.copy();
        oldSave.getCompound("OutputItems").putInt("Size", 3);
        var oldItems = oldSave.getCompound("OutputItems").getList("Items", 10);
        while (oldItems.size() > 3) oldItems.removeLast();
        restored.loadCustomOnly(oldSave, helper.getLevel().registryAccess());
        helper.assertTrue(restored.output.getSlots() == 6, "old three-slot saves retain the expanded capacity");
        assertProducts(helper, restored.output, 3);
        machine.output.clearContent();
        fillBlocked(machine.output);
        for (int i = 0; i < 4; i++) machine.tick();
        helper.assertTrue(machine.tank.getPrimaryHandler().getFluidAmount() == 100, "blocked compression preserves fluid");
        helper.assertTrue(machine.output.getStackInSlot(5).isEmpty(), "blocked compression produces no partial results");
        machine.output.clearContent();
        for (int i = 0; i < 4; i++) machine.tick();
        helper.assertTrue(!machine.output.isEmpty(), "compression resumed; speed=" + machine.getSpeed() + ", timer=" + machine.timer
                + ", fluid=" + machine.tank.getPrimaryHandler().getFluidAmount());
        assertProducts(helper, machine.output, 6);
        helper.assertTrue(machine.tank.getPrimaryHandler().isEmpty(), "compression resumes after extraction");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static <T extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity> T place(GameTestHelper helper, String block) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(block)));
        T machine = (T) helper.getBlockEntity(pos);
        machine.initialize();
        return machine;
    }

    private static Recipe<?> loaded(GameTestHelper helper, String name) {
        return helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:compat_outputs/" + name))
                .orElseThrow(() -> new AssertionError("expanded datapack recipe did not load: " + name)).value();
    }

    private static CreativeMotorBlockEntity drive(GameTestHelper helper, KineticBlockEntity machine, int rpm) {
        var axis = ((IRotate) machine.getBlockState().getBlock()).getRotationAxis(machine.getBlockState());
        var direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        var motorPos = new BlockPos(1, 1, 1).relative(direction);
        helper.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, direction.getOpposite()));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(motorPos);
        motor.generatedSpeed.setValue(rpm);
        motor.initialize();
        machine.attachKinetics();
        helper.assertTrue(Math.abs(machine.getSpeed()) == rpm && !machine.isOverStressed(), "real kinetic network drives optional machine");
        return motor;
    }

    private static ProcessingRecipe<?, ?> decode(GameTestHelper helper, String type, int count) {
        JsonObject json = new JsonObject();
        JsonArray ingredients = new JsonArray();
        JsonObject input = new JsonObject();
        if (type.startsWith("rubberworks")) {
            input.addProperty("type", "neoforge:single"); input.addProperty("fluid", "minecraft:water"); input.addProperty("amount", 100);
        } else input.addProperty("item", "minecraft:paper");
        ingredients.add(input); json.add("ingredients", ingredients);
        JsonArray results = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject output = new JsonObject();
            output.addProperty("id", BuiltInRegistries.ITEM.getKey(PRODUCTS[i % PRODUCTS.length]).toString());
            output.addProperty("chance", i == count - 1 ? 0.25 : 1.0);
            results.add(output);
        }
        json.add("results", results);
        return (ProcessingRecipe<?, ?>) BuiltInRegistries.RECIPE_SERIALIZER.get(ResourceLocation.parse(type)).codec().codec()
                .parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }

    private static void fillBlocked(IItemHandlerModifiable inventory) {
        for (int slot = 0; slot < inventory.getSlots() - 1; slot++) inventory.setStackInSlot(slot, new ItemStack(Items.DIAMOND));
    }

    private static void assertProducts(GameTestHelper helper, IItemHandler inventory, int count) {
        var contents = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) contents.add(inventory.getStackInSlot(slot));
        for (int i = 0; i < count; i++) {
            int actual = 0;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (stack.is(PRODUCTS[i])) actual += stack.getCount();
            }
            helper.assertTrue(actual == 1, "each output survives exactly once: " + PRODUCTS[i] + ", got " + actual + "; contents=" + contents);
        }
    }
}
