package net.quiwh.cwi;

import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.recipes.VatMachineRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@PrefixGameTestTemplate(false)
public final class VatCapacityGameTests {
    private static final Item[] INPUTS = {Items.PAPER, Items.WHEAT, Items.POTATO, Items.CARROT,
            Items.SUGAR_CANE, Items.STRING, Items.STICK, Items.FLINT};
    private static final Item[] OUTPUTS = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
            Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI, Items.EMERALD, Items.QUARTZ};

    @EventBusSubscriber(modid = CwiCore.ID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
            if (ModList.get().isLoaded("tfmg")) event.register(VatCapacityGameTests.class);
        }
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void vatRecipeLimitsAndNetwork(GameTestHelper helper) {
        VatMachineRecipe recipe = decode(helper, 8, 8, 4, 4);
        recipe.getRollableResults().set(7, new com.simibubi.create.content.processing.recipe.ProcessingOutput(
                new ItemStack(OUTPUTS[7], 8), 0.25f));
        helper.assertTrue(recipe.validate().isEmpty(), "eight items and four fluids on both sides validate");
        for (int[] counts : new int[][]{{9, 8, 4, 4}, {8, 9, 4, 4}, {8, 8, 5, 4}, {8, 8, 4, 5}}) {
            try {
                decode(helper, counts[0], counts[1], counts[2], counts[3]);
                throw new AssertionError("vat accepted an entry beyond its limit");
            } catch (RuntimeException rejected) {
                helper.assertTrue(rejected.getMessage().contains("more"), "native codec rejected the excessive entries");
            }
        }
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>) recipe.getSerializer();
            serializer.streamCodec().encode(buffer, recipe);
            var copy = (VatMachineRecipe) serializer.streamCodec().decode(buffer);
            helper.assertTrue(copy.getIngredients().size() == 8 && copy.getRollableResults().size() == 8,
                    "network retains eight inputs and outputs");
            helper.assertTrue(copy.getRollableResults().getLast().getChance() == 0.25f
                    && copy.getRollableResults().getLast().getStack().getCount() == 8 && buffer.readableBytes() == 0,
                    "network retains output stack counts and chances");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void vatMatchesAndProcessesAllEightSlots(GameTestHelper helper) {
        VatBlockEntity vat = place(helper);
        helper.assertTrue(vat.inputInventory.getSlots() == 8 && vat.outputInventory.getSlots() == 8,
                "vat has eight input and eight output slots");
        IItemHandler capability = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, vat.getBlockPos(), null);
        helper.assertTrue(capability != null && capability.getSlots() == 16, "automation exposes all sixteen slots");
        for (int i = 0; i < 7; i++) capability.insertItem(i, new ItemStack(INPUTS[i], 2), false);
        vat.inputTank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER, 200), FluidAction.EXECUTE);
        helper.assertTrue(vat.getMatchingRecipe() == null, "missing eighth ingredient prevents recipe matching");
        capability.insertItem(7, new ItemStack(INPUTS[7], 2), false);
        helper.assertTrue(vat.getMatchingRecipe() != null, "matcher checks slots five through eight");
        vat.recipe = vat.getMatchingRecipe();
        vat.handleRecipe(); vat.handleRecipe();
        for (int i = 0; i < 8; i++) {
            helper.assertTrue(vat.inputInventory.getStackInSlot(i).is(INPUTS[i])
                    && vat.inputInventory.getStackInSlot(i).getCount() == 1, "consumes exactly one item from input " + i);
            helper.assertTrue(total(vat.outputInventory, OUTPUTS[i]) == i + 1, "retains counted output " + i);
        }
        helper.assertTrue(vat.inputTank.getPrimaryHandler().getFluidAmount() == 100, "consumes one fluid input batch");
        helper.assertTrue(vat.outputTank.getPrimaryHandler().getFluidAmount() == 50, "emits one fluid output batch");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void vatWaitsForEightOutputsWithoutConsumingInputs(GameTestHelper helper) {
        VatBlockEntity vat = place(helper);
        for (int i = 0; i < 8; i++) vat.inputInventory.setStackInSlot(i, new ItemStack(INPUTS[i]));
        vat.inputTank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        VatMachineRecipe recipe = vat.getMatchingRecipe();
        helper.assertTrue(recipe != null, "expanded datapack recipe loads and matches");
        for (int i = 0; i < 7; i++) vat.outputInventory.setStackInSlot(i, new ItemStack(Items.DIAMOND, 64));
        vat.recipe = recipe;
        vat.handleRecipe(); vat.handleRecipe();
        for (int i = 0; i < 8; i++) helper.assertTrue(vat.inputInventory.getStackInSlot(i).getCount() == 1,
                "full output buffer preserves input " + i);
        helper.assertTrue(vat.inputTank.getPrimaryHandler().getFluidAmount() == 100
                && vat.outputTank.isEmpty() && vat.outputInventory.getStackInSlot(7).isEmpty(),
                "blocked batch preserves fluids and writes no partial results");
        vat.outputInventory.clearContent();
        vat.recipe = recipe;
        vat.handleRecipe(); vat.handleRecipe();
        for (int i = 0; i < 8; i++) helper.assertTrue(total(vat.outputInventory, OUTPUTS[i]) == i + 1,
                "resumes with output " + i);
        helper.assertTrue(vat.inputInventory.isEmpty() && vat.inputTank.isEmpty(), "resumed batch consumes its inputs once");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void vatEightSlotAndLegacySaves(GameTestHelper helper) {
        VatBlockEntity vat = place(helper);
        for (int i = 0; i < 8; i++) {
            vat.inputInventory.setStackInSlot(i, new ItemStack(INPUTS[i], i + 1));
            vat.outputInventory.setStackInSlot(i, new ItemStack(OUTPUTS[i], i + 2));
        }
        var saved = vat.saveWithoutMetadata(helper.getLevel().registryAccess());
        VatBlockEntity restored = (VatBlockEntity) vat.getType().create(vat.getBlockPos(), vat.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
        for (int i = 0; i < 8; i++) helper.assertTrue(total(restored.inputInventory, INPUTS[i]) == i + 1
                && total(restored.outputInventory, OUTPUTS[i]) == i + 2, "new save preserves slot " + i);
        var legacy = saved.copy();
        for (String key : new String[]{"InputItems", "OutputItems"}) {
            var inventory = legacy.getCompound(key);
            inventory.putInt("Size", 4);
            var items = inventory.getList("Items", 10);
            for (int i = items.size() - 1; i >= 0; i--) if (items.getCompound(i).getInt("Slot") >= 4) items.remove(i);
        }
        restored.loadCustomOnly(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(restored.inputInventory.getSlots() == 8 && restored.outputInventory.getSlots() == 8,
                "old four-slot saves expand both inventories");
        for (int i = 0; i < 4; i++) helper.assertTrue(total(restored.inputInventory, INPUTS[i]) == i + 1
                && total(restored.outputInventory, OUTPUTS[i]) == i + 2, "legacy save preserves slot " + i);
        helper.assertTrue(legacy.getCompound("InputItems").getInt("Size") == 4,
                "loading does not mutate the supplied save tag");
        helper.succeed();
    }

    private static VatBlockEntity place(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tfmg:steel_chemical_vat")));
        VatBlockEntity vat = helper.getBlockEntity(new BlockPos(1, 1, 1));
        vat.initialize();
        return vat;
    }

    private static int total(IItemHandler inventory, Item item) {
        int result = 0;
        for (int i = 0; i < inventory.getSlots(); i++) if (inventory.getStackInSlot(i).is(item))
            result += inventory.getStackInSlot(i).getCount();
        return result;
    }

    private static VatMachineRecipe decode(GameTestHelper helper, int itemsIn, int itemsOut, int fluidsIn, int fluidsOut) {
        var json = new JsonObject();
        var ingredients = new JsonArray(); var results = new JsonArray();
        for (int i = 0; i < itemsIn; i++) { var item = new JsonObject();
            item.addProperty("item", BuiltInRegistries.ITEM.getKey(INPUTS[i % 8]).toString()); ingredients.add(item); }
        for (int i = 0; i < itemsOut; i++) { var item = new JsonObject();
            item.addProperty("id", BuiltInRegistries.ITEM.getKey(OUTPUTS[i % 8]).toString()); item.addProperty("count", i + 1); results.add(item); }
        for (int i = 0; i < fluidsIn; i++) { var fluid = new JsonObject();
            fluid.addProperty("type", "neoforge:single"); fluid.addProperty("fluid", "minecraft:water");
            fluid.addProperty("amount", 100); ingredients.add(fluid); }
        for (int i = 0; i < fluidsOut; i++) { var fluid = new JsonObject();
            fluid.addProperty("id", "minecraft:lava"); fluid.addProperty("amount", 50); results.add(fluid); }
        json.add("ingredients", ingredients); json.add("results", results); json.addProperty("processing_time", 1);
        return (VatMachineRecipe) BuiltInRegistries.RECIPE_SERIALIZER.get(ResourceLocation.parse("tfmg:vat_machine_recipe"))
                .codec().codec().parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }
}
