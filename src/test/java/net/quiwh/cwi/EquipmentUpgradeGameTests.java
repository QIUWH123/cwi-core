package net.quiwh.cwi;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.quiwh.cwi.equipment.EquipmentUpgradeRecipe;
import net.quiwh.cwi.equipment.UpgradeIngredient;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class EquipmentUpgradeGameTests {
    static EquipmentUpgradeRecipe script(net.minecraft.world.level.Level level, String path) {
        return (EquipmentUpgradeRecipe)level.getRecipeManager().byKey(ResourceLocation.parse("cwi:" + path)).orElseThrow().value();
    }
    static java.util.Map<String, String> scriptFingerprint(net.minecraft.world.level.Level level) {
        var recipes = new java.util.TreeMap<String, String>();
        level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).forEach(holder -> {
            if (holder.value() instanceof EquipmentUpgradeRecipe) recipes.put(holder.id().toString(),
                Recipe.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), holder.value()).getOrThrow().toString());
        });
        return recipes;
    }
    static void verifyScripts(net.minecraft.world.level.Level level) {
        var component = script(level, "equipment_upgrade/contract_component/cwi/contract_iron/axe_manual_only");
        if (component.maxLevel() != 3 || component.durabilityMultiplier() != .1
                || component.ingredients().getFirst().count() != 2
                || !component.ingredients().getFirst().ingredient().test(new ItemStack(Items.GOLD_INGOT))
                || component.ingredients().get(1).consume() || component.ingredients().get(1).damage() != 2)
            throw new AssertionError("material expansion and normal KubeJS replacement must preserve counts, cap, multiplier and tool wear");
        var direct = script(level, "equipment_upgrade/contract_direct_manual_only");
        if (direct.ingredients().getFirst().count() != 2 || direct.ingredients().get(1).consume()
                || direct.ingredients().get(1).damage() != 0
                || !direct.ingredients().get(1).ingredient().test(new ItemStack(Items.SAND)))
            throw new AssertionError("custom event supports counted strings, Ingredient.of tags, and reusable nondamageable ingredients");
        if (script(level, "equipment_upgrade/contract_fixed/cwi/contract_iron/axe_manual_only").maxLevel() != 5
                || level.getRecipeManager().byKey(ResourceLocation.parse("cwi:equipment_upgrade/contract_removed_manual_only")).isPresent())
            throw new AssertionError("fixed cap and ordinary recipe removal");
        var output = script(level, "contracts/upgrade_output_manual_only");
        if (!output.output().is(Items.DIAMOND_HOE) || output.ingredients().getFirst().count() != 2)
            throw new AssertionError("zero-argument fluent schema keeps explicit output and ingredient data");
        var adapter = script(level, "equipment_upgrade/1_manual_only");
        if (!adapter.enchantment().is(Enchantments.SWEEPING_EDGE) || adapter.maxLevel() != 2
                || adapter.durabilityMultiplier() != .12 || adapter.ingredients().getFirst().count() != 2
                || adapter.ingredients().get(1).consume() || adapter.ingredients().get(1).damage() != 1)
            throw new AssertionError("original registration helper signature, counts, reusable tools, enchantment rename and numeric id");
        var adapterOutput = script(level, "equipment_upgrade/2_manual_only");
        if (!adapterOutput.output().is(Items.IRON_HOE) || !adapterOutput.input().test(new ItemStack(Items.STONE_HOE)))
            throw new AssertionError("registration helper preserves optional changed output");
        script(level, "example/feather_falling_manual_only");
        script(level, "equipment_upgrade/example_sharpness/cwi/example_iron/sword_manual_only");
        script(level, "equipment_upgrade/example_unbreaking/cwi/example_iron/pickaxe_manual_only");
    }
    static CraftingRecipe recipe(GameTestHelper h) {
        var holder = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:equipment_contracts/sharpness"));
        h.assertTrue(holder.isPresent(), "native equipment upgrade serializer must load the datapack recipe");
        return (CraftingRecipe)holder.orElseThrow().value();
    }
    static CraftingInput grid(ItemStack tool, ItemStack shears) {
        return CraftingInput.of(2, 2, List.of(tool, new ItemStack(Items.IRON_INGOT), shears, new ItemStack(Items.IRON_INGOT)));
    }
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void upgradePreservesComponentsAndHistoricalWear(GameTestHelper h) {
        var r = recipe(h); var tool = new ItemStack(Items.IRON_SWORD);
        var sharpness = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        var unbreaking = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Original name")); tool.setDamageValue(50); tool.enchant(unbreaking, 2);
        var before = tool.copy(); var input = grid(tool, new ItemStack(Items.SHEARS));
        h.assertTrue(r.matches(input, h.getLevel()), "upgrade matches damaged/enchanted equipment");
        var result = r.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(result.getEnchantmentLevel(sharpness) == 1 && result.getEnchantmentLevel(unbreaking) == 2
                && result.getHoverName().getString().equals("Original name") && result.getDamageValue() == 90,
                "first upgrade retains components and costs floor((250-50)*0.2*sqrt(1))=40 durability");
        h.assertTrue(ItemStack.matches(before, tool), "preview cannot mutate inputs");
        int expected = Math.min(249, 90 + Math.max(1, (int)Math.floor(160 * 0.2 * Math.sqrt(3))));
        var next = r.assemble(grid(result, new ItemStack(Items.SHEARS)), h.getLevel().registryAccess());
        h.assertTrue(next.getEnchantmentLevel(sharpness) == 2 && next.getDamageValue() == expected,
                "repeat upgrade uses historical enchantment-index formula");
        next.enchant(sharpness, 3);
        h.assertTrue(!r.matches(grid(next, new ItemStack(Items.SHEARS)), h.getLevel())
                && r.assemble(grid(next, new ItemStack(Items.SHEARS)), h.getLevel().registryAccess()).isEmpty(), "cap blocks matching and output");
        tool.setDamageValue(249); var worn = r.assemble(grid(tool, new ItemStack(Items.SHEARS)), h.getLevel().registryAccess());
        h.assertTrue(!worn.isEmpty() && worn.getDamageValue() == 249, "upgrading never destroys equipment");
        h.succeed();
    }
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void upgradeWearRetainsInsertionOrderAfterSave(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var a = lookup.getOrThrow(Enchantments.SHARPNESS);
        var b = lookup.getOrThrow(Enchantments.UNBREAKING);
        var c = lookup.getOrThrow(Enchantments.MENDING);
        var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for (var order : List.of(List.of(a,b,c), List.of(c,b,a), List.of(b,a,c), List.of(c,a,b))) {
            ItemStack tool = new ItemStack(Items.DIAMOND_SWORD);
            for (var enchantment : order) {
                var r = new EquipmentUpgradeRecipe(Ingredient.of(Items.DIAMOND_SWORD), ItemStack.EMPTY,
                    List.of(new UpgradeIngredient(Ingredient.of(Items.FLINT), 1, true, 0)), enchantment, 5, .01);
                tool = r.assemble(CraftingInput.of(2,1,List.of(tool,new ItemStack(Items.FLINT))),registries);
            }
            tool = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops,tool).getOrThrow()).getOrThrow();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                ItemStack.STREAM_CODEC.encode(buffer, tool);
                var received = ItemStack.STREAM_CODEC.decode(buffer);
                h.assertTrue(ItemStack.matches(tool, received) && buffer.readableBytes() == 0,
                    "item synchronization retains ordered upgrade metadata");
                tool = received;
            } finally { buffer.release(); }
            for (int index=0; index<order.size(); index++) {
                var r = new EquipmentUpgradeRecipe(Ingredient.of(Items.DIAMOND_SWORD), ItemStack.EMPTY,
                    List.of(new UpgradeIngredient(Ingredient.of(Items.FLINT), 1, true, 0)), order.get(index), 5, .01);
                int expected = tool.getDamageValue() + Math.max(1,(int)Math.floor(
                    (tool.getMaxDamage()-tool.getDamageValue()) * .01 * Math.sqrt(index+2)));
                var result = r.assemble(CraftingInput.of(2,1,List.of(tool,new ItemStack(Items.FLINT))),registries);
                h.assertTrue(result.getDamageValue()==expected,
                    "wear must use original insertion position "+index+" after item save/reload, independent of map iteration");
            }
        }
        h.succeed();
    }
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void upgradeWearHonorsRetainedLegacyOrder(GameTestHelper h) throws Exception {
        var registries = h.getLevel().registryAccess();
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var tool = new ItemStack(Items.DIAMOND_SWORD);
        tool.enchant(lookup.getOrThrow(Enchantments.SHARPNESS), 1);
        tool.enchant(lookup.getOrThrow(Enchantments.UNBREAKING), 1);
        tool.enchant(lookup.getOrThrow(Enchantments.MENDING), 1);
        tool.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(
            net.minecraft.nbt.TagParser.parseTag("{keep:7,Enchantments:[{id:'minecraft:unbreaking'},{id:'minecraft:mending'},{id:'minecraft:sharpness'}]}")));
        var r = new EquipmentUpgradeRecipe(Ingredient.of(Items.DIAMOND_SWORD), ItemStack.EMPTY,
            List.of(new UpgradeIngredient(Ingredient.of(Items.FLINT), 1, true, 0)),
            lookup.getOrThrow(Enchantments.UNBREAKING), 5, .01);
        var result = r.assemble(CraftingInput.of(2, 1, List.of(tool, new ItemStack(Items.FLINT))), registries);
        h.assertTrue(result.getDamageValue() == (int)Math.floor(1561 * .01 * Math.sqrt(2)),
            "retained original list determines wear independently of the native enchantment map");
        var data = result.get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertTrue(data.getInt("keep") == 7 && data.getList("Enchantments", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 3,
            "legacy recovery preserves unrelated custom data and the migration source list");
        var order = data.getList("cwi:enchantment_order", net.minecraft.nbt.Tag.TAG_STRING);
        h.assertTrue(order.size() == 3 && order.getString(0).equals("minecraft:unbreaking")
            && order.getString(1).equals("minecraft:mending") && order.getString(2).equals("minecraft:sharpness"),
            "recovered order becomes persistent upgrade history");
        h.succeed();
    }
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void upgradeRemaindersAndCounts(GameTestHelper h) {
        var r = recipe(h); var shears = new ItemStack(Items.SHEARS); shears.setDamageValue(10);
        var input = grid(new ItemStack(Items.IRON_SWORD), shears); var remainder = r.getRemainingItems(input);
        h.assertTrue(remainder.get(2).is(Items.SHEARS) && remainder.get(2).getDamageValue() == 11
                && remainder.get(0).isEmpty() && remainder.get(1).isEmpty() && remainder.get(3).isEmpty()
                && shears.getDamageValue() == 10, "catalyst takes one wear and other entries consume without preview mutation");
        shears.setDamageValue(shears.getMaxDamage() - 1);
        h.assertTrue(r.getRemainingItems(grid(new ItemStack(Items.IRON_SWORD), shears)).get(2).isEmpty(), "worn catalyst breaks exactly at durability");
        var missing = CraftingInput.of(2, 2, List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_INGOT, 2),
                new ItemStack(Items.SHEARS), ItemStack.EMPTY));
        h.assertTrue(!r.matches(missing, h.getLevel()), "counted ingredients require separate shapeless slots as in original");
        var extras = new ArrayList<>(input.items()); extras.add(new ItemStack(Items.DIRT)); extras.add(ItemStack.EMPTY);
        h.assertTrue(!r.matches(CraftingInput.of(3, 2, extras), h.getLevel()), "extra ingredients reject crafting");
        h.succeed();
    }
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void upgradeOverlapContainersAndAlternateOutput(GameTestHelper h) throws Exception {
        var enchantment = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        var overlap = new EquipmentUpgradeRecipe(Ingredient.of(Items.IRON_SWORD), ItemStack.EMPTY, List.of(
            new UpgradeIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1, true, 0),
            new UpgradeIngredient(Ingredient.of(Items.IRON_INGOT), 1, true, 0)), enchantment, 3, .1);
        var input = CraftingInput.of(3, 1, List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)));
        h.assertTrue(overlap.matches(input, h.getLevel()), "overlapping ingredients backtrack instead of greedily rejecting valid grids");
        var containers = new EquipmentUpgradeRecipe(Ingredient.of(Items.IRON_SWORD), new ItemStack(Items.DIAMOND_SWORD),
            List.of(new UpgradeIngredient(Ingredient.of(Items.WATER_BUCKET), 1, true, 0)), enchantment, 3, .2);
        var tool = new ItemStack(Items.IRON_SWORD); tool.setDamageValue(30);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
        tool.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(net.minecraft.nbt.TagParser.parseTag("{upgrade_test:7}")));
        var changed = containers.assemble(CraftingInput.of(2, 1, List.of(tool, new ItemStack(Items.WATER_BUCKET))), h.getLevel().registryAccess());
        h.assertTrue(changed.is(Items.DIAMOND_SWORD) && changed.getHoverName().getString().equals("Keep me")
            && changed.get(DataComponents.CUSTOM_DATA).copyTag().getInt("upgrade_test") == 7
            && changed.getEnchantmentLevel(enchantment) == 1
            && changed.getDamageValue() == 30 + (int)Math.floor((1561 - 30) * .2), "alternate item retains custom components and uses output durability");
        h.assertTrue(containers.getRemainingItems(CraftingInput.of(2, 1, List.of(tool, new ItemStack(Items.WATER_BUCKET)))).get(1).is(Items.BUCKET),
            "ordinary consumed ingredient preserves native container remainder");
        var zeroCost = new EquipmentUpgradeRecipe(Ingredient.of(Items.IRON_SWORD), ItemStack.EMPTY,
            List.of(new UpgradeIngredient(Ingredient.of(Items.FLINT), 1, true, 0)), enchantment, 3, 0);
        h.assertTrue(zeroCost.assemble(CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.FLINT))),
            h.getLevel().registryAccess()).getDamageValue() == 1, "zero multiplier retains historical minimum one wear");
        h.succeed();
    }
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void upgradeValidationAndAutomation(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var r = recipe(h);
        var json = Recipe.CODEC.encodeStart(ops, r).getOrThrow().getAsJsonObject();
        for (var bad : List.of("max_level", "durability_multiplier")) {
            var invalid = json.deepCopy(); invalid.addProperty(bad, -1);
            h.assertTrue(Recipe.CODEC.parse(ops, invalid).error().isPresent(), "negative upgrade limits/costs rejected by codec");
        }
        var oversized = json.deepCopy(); oversized.getAsJsonArray("ingredients").get(0).getAsJsonObject().addProperty("count", 8);
        h.assertTrue(Recipe.CODEC.parse(ops, oversized).error().isPresent(), "expanded ingredient counts must fit beside equipment");
        var sharpness = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        var smite = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE);
        var tool = new ItemStack(Items.IRON_SWORD); tool.enchant(smite, 1);
        var result = r.assemble(grid(tool, new ItemStack(Items.SHEARS)), registries);
        h.assertTrue(result.getEnchantmentLevel(smite) == 1 && result.getEnchantmentLevel(sharpness) == 1,
            "original upgrade rules retain unusual/vanilla-incompatible enchantment combinations");
        var saved = ItemStack.CODEC.encodeStart(ops, result).getOrThrow();
        var loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        h.assertTrue(ItemStack.matches(loaded, result) && r.matches(grid(loaded, new ItemStack(Items.SHEARS)), h.getLevel()),
            "native item save/reload retains enchantments, damage and upgrade eligibility");
        var holder = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("cwi:equipment_contracts/sharpness")).orElseThrow();
        h.assertTrue(com.simibubi.create.AllRecipeTypes.shouldIgnoreInAutomation(holder),
            "Create automation ignores serializer even for recipes without manual_only suffix");
        h.succeed();
    }
    @GameTest(template = "machine_empty", templateNamespace = CwiCore.ID)
    public static void upgradeNetworkAndRealCrafting(GameTestHelper h) {
        var r = recipe(h); var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            @SuppressWarnings("unchecked") var serializer = (RecipeSerializer<Recipe<?>>)r.getSerializer();
            serializer.streamCodec().encode(buffer, r);
            var copy = (CraftingRecipe)serializer.streamCodec().decode(buffer);
            h.assertTrue(copy.matches(grid(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHEARS)), h.getLevel())
                    && buffer.readableBytes() == 0, "native network codec retains upgrade data");
        } finally { buffer.release(); }
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var menu = new CraftingMenu(19, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3))));
        menu.getSlot(1).set(new ItemStack(Items.IRON_SWORD)); menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT, 3));
        menu.getSlot(4).set(new ItemStack(Items.SHEARS)); menu.getSlot(5).set(new ItemStack(Items.IRON_INGOT, 2));
        h.assertTrue(!menu.getSlot(0).getItem().isEmpty() && menu.getSlot(0).getItem().is(Items.IRON_SWORD),
                "real crafting menu selects upgrade output");
        // The real ResultSlot consumes via RecipeManager and installs native remainders.
        var output = r.assemble(grid(menu.getSlot(1).getItem(), menu.getSlot(4).getItem()), h.getLevel().registryAccess());
        menu.getSlot(0).onTake(player, output);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().getCount() == 2
                && menu.getSlot(5).getItem().getCount() == 1 && menu.getSlot(4).getItem().getDamageValue() == 1,
                "real crafting consumes one equipment and each ingredient while returning damaged catalyst");
        h.succeed();
    }
}
