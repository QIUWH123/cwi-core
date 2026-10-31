package net.quiwh.cwi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import fr.lucreeper74.createmetallurgy.content.blocks.foundry_basin.FoundryBasinBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class FoundryCapacityGameTests {
    private static final Item[] INPUTS = {Items.PAPER, Items.WHEAT, Items.CARROT, Items.FLINT};
    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("createmetallurgy")) event.register(FoundryCapacityGameTests.class);
        }
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void foundryStackCapacityCodecsAndNetwork(GameTestHelper helper) {
        for (String type : new String[]{"melting", "alloying"}) {
            for (int count : new int[]{4, 5, 8, 36}) {
                var recipe = decode(helper, type, count);
                helper.assertTrue(recipe.validate().isEmpty(), type + " accepts " + count + " item entries");
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                try {
                    @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>)recipe.getSerializer();
                    serializer.streamCodec().encode(buffer, recipe);
                    var copy = (BasinRecipe)serializer.streamCodec().decode(buffer);
                    helper.assertTrue(copy.getIngredients().size() == count
                                    && copy.getRollableResults().getFirst().getStack().getCount() == 2
                                    && copy.getProcessingDuration() == 1 && copy.getRequiredHeat() == HeatCondition.NONE
                                    && buffer.readableBytes() == 0,
                            type + " network preserves all entries, counted output, duration and heat");
                } finally { buffer.release(); }
            }
            try { decode(helper, type, 37); throw new AssertionError(type + " accepted more than four nine-item stacks"); }
            catch (RuntimeException expected) {
                helper.assertTrue(expected.getMessage().contains("more item inputs (37) than supported (36)"),
                        "native codec rejects recipes larger than total physical item capacity");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void foundryFiveRepeatedInputsConsumeExactCounts(GameTestHelper helper) {
        verifyRepeatedInputs(helper, 1, 400);
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void foundryEightRepeatedInputsConsumeExactCounts(GameTestHelper helper) {
        verifyRepeatedInputs(helper, 4, 300);
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void foundryConsumesFourFullNineItemStacks(GameTestHelper helper) {
        var basin = place(helper);
        var recipe = decode(helper, "alloying", 36);
        for (int i = 0; i < 4; i++) basin.inputInventory.setStackInSlot(i, new ItemStack(INPUTS[i], 9));
        helper.assertTrue(BasinRecipe.match(basin, recipe) && BasinRecipe.apply(basin, recipe),
                "a full four-slot inventory can supply 36 ingredient entries");
        for (int i = 0; i < 4; i++) helper.assertTrue(basin.inputInventory.getStackInSlot(i).isEmpty(),
                "all nine items in input slot " + i + " are consumed exactly once");
        helper.assertTrue(basin.getOutputInventory().getStackInSlot(0).is(Items.BRICK)
                        && basin.getOutputInventory().getStackInSlot(0).getCount() == 2,
                "full-stack recipe keeps its counted output");
        helper.succeed();
    }
    private static void verifyRepeatedInputs(GameTestHelper helper, int firstCount, int amount) {
        helper.setBlock(new BlockPos(1, 0, 1), AllBlocks.BLAZE_BURNER.getDefaultState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING));
        var basin = place(helper);
        helper.assertTrue(basin.inputInventory.getSlots() == 4 && basin.inputInventory.getSlotLimit(0) == 9,
                "repeated recipe entries do not expand physical slots or their nine-item limit");
        var json = new JsonObject(); var ingredients = new JsonArray(); var results = new JsonArray();
        for (int i = 0; i < firstCount + 4; i++) {
            var item = new JsonObject(); item.addProperty("item", i < firstCount ? "minecraft:paper" : "minecraft:wheat");
            ingredients.add(item);
        }
        var fluid = new JsonObject(); fluid.addProperty("id", "minecraft:water"); fluid.addProperty("amount", amount); results.add(fluid);
        json.add("ingredients", ingredients); json.add("results", results);
        json.addProperty("processing_time", 360); json.addProperty("heat_requirement", "superheated");
        var recipe = decode(helper, "alloying", json);
        helper.assertTrue(recipe.getIngredients().size() == firstCount + 4 && recipe.getProcessingDuration() == 360
                        && recipe.getRequiredHeat() == HeatCondition.SUPERHEATED && recipe.getRollableResults().isEmpty()
                        && recipe.getFluidResults().getFirst().getAmount() == amount,
                "repeated input recipe retains every ingredient, duration, heat and fluid-only output");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>)recipe.getSerializer();
            serializer.streamCodec().encode(buffer, recipe);
            recipe = (BasinRecipe)serializer.streamCodec().decode(buffer);
            helper.assertTrue(recipe.getIngredients().size() == firstCount + 4 && recipe.getProcessingDuration() == 360
                            && recipe.getRequiredHeat() == HeatCondition.SUPERHEATED
                            && recipe.getFluidResults().getFirst().getAmount() == amount && buffer.readableBytes() == 0,
                    "network preserves repeated ingredients, heat, time and fluid amount");
        } finally { buffer.release(); }
        basin.inputInventory.setStackInSlot(0, new ItemStack(Items.PAPER, firstCount + 1));
        basin.inputInventory.setStackInSlot(1, new ItemStack(Items.WHEAT, 3));
        basin.inputInventory.setStackInSlot(2, new ItemStack(Items.CARROT, 2));
        helper.assertTrue(!BasinRecipe.match(basin, recipe) && !BasinRecipe.apply(basin, recipe),
                "a missing repeated item rejects both matching and processing");
        helper.assertTrue(basin.inputInventory.getStackInSlot(0).getCount() == firstCount + 1
                        && basin.inputInventory.getStackInSlot(1).getCount() == 3
                        && basin.inputInventory.getStackInSlot(2).getCount() == 2
                        && basin.getOutputTank().getPrimaryHandler().isEmpty() && basin.getOutputInventory().isEmpty(),
                "insufficient repeated inputs cannot partially consume items or produce output");
        basin.inputInventory.setStackInSlot(1, new ItemStack(Items.WHEAT, 5));
        helper.assertTrue(BasinRecipe.match(basin, recipe), "full repeated ingredient count matches");
        helper.assertTrue(basin.inputInventory.getStackInSlot(0).getCount() == firstCount + 1
                        && basin.inputInventory.getStackInSlot(1).getCount() == 5,
                "matching repeated ingredients does not consume them");
        helper.assertTrue(BasinRecipe.apply(basin, recipe), "repeated input recipe processes through native basin logic");
        helper.assertTrue(basin.inputInventory.getStackInSlot(0).getCount() == 1
                        && basin.inputInventory.getStackInSlot(1).getCount() == 1
                        && basin.inputInventory.getStackInSlot(2).getCount() == 2,
                "native processing consumes exactly the repeated item counts and leaves unrelated items");
        helper.assertTrue(basin.getOutputTank().getPrimaryHandler().getFluidAmount() == amount
                        && basin.getOutputInventory().isEmpty(), "native processing emits exactly the original fluid amount");
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void foundryBothRecipesUseFourthPhysicalSlot(GameTestHelper helper) {
        var basin = place(helper);
        helper.assertTrue(basin.inputInventory.getSlots() == 4,
                "four physical input and output slots");
        var inventory = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, basin.getBlockPos(), null);
        helper.assertTrue(inventory != null && inventory.getSlots() == 8 && inventory.getSlotLimit(3) == 9,
                "automation exposes fourth input with native nine-item stack limit");
        for (String type : new String[]{"melting", "alloying"}) {
            var recipe = decode(helper, type, 4);
            for (int i = 0; i < 3; i++) inventory.insertItem(i, new ItemStack(INPUTS[i], 2), false);
            helper.assertTrue(!BasinRecipe.match(basin, recipe), type + " requires fourth ingredient");
            inventory.insertItem(3, new ItemStack(INPUTS[3], 2), false);
            helper.assertTrue(BasinRecipe.match(basin, recipe) && BasinRecipe.apply(basin, recipe), type + " processes four inputs");
            for (int i = 0; i < 4; i++) helper.assertTrue(basin.inputInventory.getStackInSlot(i).getCount() == 1,
                    type + " consumes exactly one from slot " + i);
            helper.assertTrue(inventory.getStackInSlot(4).is(Items.BRICK)
                    && inventory.getStackInSlot(4).getCount() == 2, type + " retains output");
            basin.inputInventory.clearContent(); for (int i = 4; i < 8; i++) inventory.extractItem(i, 64, false);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void foundryNewAndLegacySaves(GameTestHelper helper) {
        var basin = place(helper);
        for (int i = 0; i < 4; i++) basin.inputInventory.setStackInSlot(i, new ItemStack(INPUTS[i], i + 1));
        var saved = basin.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = (FoundryBasinBlockEntity)basin.getType().create(basin.getBlockPos(), basin.getBlockState());
        restored.setLevel(helper.getLevel()); restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.inputInventory.getSlots() == 4 && restored.inputInventory.getStackInSlot(3).getCount() == 4,
                "new fourth slot survives save/load");
        var legacy = saved.copy(); var input = legacy.getCompound("InputItems"); input.putInt("Size", 3);
        var entries = input.getList("Items", 10);
        for (int i = entries.size() - 1; i >= 0; i--) if (entries.getCompound(i).getInt("Slot") >= 3) entries.remove(i);
        restored.loadCustomOnly(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(restored.inputInventory.getSlots() == 4 && restored.inputInventory.getStackInSlot(3).isEmpty(),
                "legacy three-slot inventory expands");
        for (int i = 0; i < 3; i++) helper.assertTrue(restored.inputInventory.getStackInSlot(i).getCount() == i + 1, "legacy contents preserved");
        helper.assertTrue(input.getInt("Size") == 3, "source NBT is unchanged");
        helper.setBlock(new BlockPos(3, 1, 1), AllBlocks.BASIN.get());
        BasinBlockEntity ordinary = helper.getBlockEntity(new BlockPos(3, 1, 1));
        helper.assertTrue(ordinary.inputInventory.getSlots() == 9, "ordinary Create basin capacity stays native");
        helper.succeed();
    }
    private static FoundryBasinBlockEntity place(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createmetallurgy:foundry_basin")));
        FoundryBasinBlockEntity basin = helper.getBlockEntity(new BlockPos(1, 1, 1)); basin.initialize(); return basin;
    }
    private static BasinRecipe decode(GameTestHelper helper, String type, int count) {
        var json = new JsonObject(); var ingredients = new JsonArray(); var results = new JsonArray();
        for (int i = 0; i < count; i++) { var item = new JsonObject();
            item.addProperty("item", BuiltInRegistries.ITEM.getKey(INPUTS[i % 4]).toString()); ingredients.add(item); }
        var output = new JsonObject(); output.addProperty("id", "minecraft:brick"); output.addProperty("count", 2); results.add(output);
        json.add("ingredients", ingredients); json.add("results", results); json.addProperty("processing_time", 1);
        return decode(helper, type, json);
    }
    private static BasinRecipe decode(GameTestHelper helper, String type, JsonObject json) {
        return (BasinRecipe)BuiltInRegistries.RECIPE_SERIALIZER.get(ResourceLocation.parse("createmetallurgy:" + type))
                .codec().codec().parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }
}
