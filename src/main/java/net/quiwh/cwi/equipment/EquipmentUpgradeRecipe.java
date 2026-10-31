package net.quiwh.cwi.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;

/** Dynamic shapeless crafting, with all content supplied by synced recipe data. */
public final class EquipmentUpgradeRecipe implements CraftingRecipe {
    private final Ingredient input;
    private final ItemStack output;
    private final List<UpgradeIngredient> ingredients;
    private final List<UpgradeIngredient> slots;
    private final Holder<Enchantment> enchantment;
    private final int maxLevel;
    private final double durabilityMultiplier;

    public EquipmentUpgradeRecipe(Ingredient input, ItemStack output, List<UpgradeIngredient> ingredients,
                                  Holder<Enchantment> enchantment, int maxLevel, double durabilityMultiplier) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("Equipment input cannot be empty");
        if (!output.isEmpty() && output.getCount() != 1) throw new IllegalArgumentException("Equipment output must contain one item");
        if (ingredients.isEmpty() || ingredients.size() > 8) throw new IllegalArgumentException("Upgrades require 1 to 8 ingredient slots");
        this.slots = ingredients.stream().flatMap(i -> java.util.Collections.nCopies(i.count(), i).stream()).toList();
        if (slots.size() > 8) throw new IllegalArgumentException("Equipment plus ingredients must fit the 3x3 crafting grid");
        if (enchantment == null || maxLevel < 1 || maxLevel > 255) throw new IllegalArgumentException("Upgrade cap must be in [1,255]");
        if (!Double.isFinite(durabilityMultiplier) || durabilityMultiplier < 0) throw new IllegalArgumentException("Durability multiplier must be finite and nonnegative");
        this.input = input; this.output = output.copy(); this.ingredients = List.copyOf(ingredients);
        this.enchantment = enchantment; this.maxLevel = maxLevel; this.durabilityMultiplier = durabilityMultiplier;
    }

    public Ingredient input() { return input; }
    public ItemStack output() { return output.copy(); }
    public List<UpgradeIngredient> ingredients() { return ingredients; }
    public Holder<Enchantment> enchantment() { return enchantment; }
    public int maxLevel() { return maxLevel; }
    public double durabilityMultiplier() { return durabilityMultiplier; }

    /** result[0] is equipment; the rest map each expanded ingredient to its grid slot. */
    private int[] assignment(CraftingInput grid) {
        if (grid.ingredientCount() != slots.size() + 1) return null;
        for (int equipment = 0; equipment < grid.size(); equipment++) {
            ItemStack stack = grid.getItem(equipment);
            if (stack.isEmpty() || !input.test(stack) || EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(enchantment) >= maxLevel) continue;
            boolean[] used = new boolean[grid.size()]; used[equipment] = true;
            int[] assigned = new int[slots.size() + 1]; Arrays.fill(assigned, -1); assigned[0] = equipment;
            if (assign(grid, 0, used, assigned)) return assigned;
        }
        return null;
    }

    private boolean assign(CraftingInput grid, int index, boolean[] used, int[] assigned) {
        if (index == slots.size()) return true;
        for (int slot = 0; slot < grid.size(); slot++) {
            if (used[slot] || grid.getItem(slot).isEmpty() || !slots.get(index).ingredient().test(grid.getItem(slot))) continue;
            used[slot] = true; assigned[index + 1] = slot;
            if (assign(grid, index + 1, used, assigned)) return true;
            used[slot] = false;
        }
        return false;
    }

    @Override public boolean matches(CraftingInput grid, Level level) { return assignment(grid) != null; }
    @Override public ItemStack assemble(CraftingInput grid, HolderLookup.Provider registries) {
        int[] assigned = assignment(grid);
        return assigned == null ? ItemStack.EMPTY : upgrade(grid.getItem(assigned[0]));
    }

    private ItemStack upgrade(ItemStack equipment) {
        ItemStack result = output.isEmpty() ? equipment.copyWithCount(1) : equipment.transmuteCopy(output.getItem(), 1);
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
        var order = UpgradeEnchantmentOrder.read(result, enchantments);
        String enchantmentId = enchantment.unwrapKey().orElseThrow().location().toString();
        int targetIndex = order.indexOf(enchantmentId);
        int damage = result.getDamageValue(), maxDamage = result.getMaxDamage();
        if (maxDamage > damage) {
            // Preserve the original ordered-list position, independently of native map iteration.
            double increment = Math.max(1, Math.floor((maxDamage - damage) * durabilityMultiplier * Math.sqrt(targetIndex + 2)));
            result.setDamageValue((int)Math.min(maxDamage - 1D, damage + increment));
        }
        int nextLevel = enchantments.getLevel(enchantment) + 1;
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(enchantments);
        mutable.set(enchantment, nextLevel);
        EnchantmentHelper.setEnchantments(result, mutable.toImmutable());
        if (targetIndex < 0) order.add(enchantmentId);
        UpgradeEnchantmentOrder.write(result, order);
        return result;
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput grid) {
        var remaining = NonNullList.withSize(grid.size(), ItemStack.EMPTY);
        int[] assigned = assignment(grid);
        if (assigned == null) return remaining;
        for (int index = 0; index < slots.size(); index++) {
            int slot = assigned[index + 1]; var definition = slots.get(index); ItemStack stack = grid.getItem(slot);
            if (definition.consume()) {
                if (stack.hasCraftingRemainingItem()) remaining.set(slot, stack.getCraftingRemainingItem());
            } else {
                ItemStack tool = stack.copyWithCount(1);
                if (tool.isDamageableItem() && definition.damage() > 0) {
                    long damage = (long)tool.getDamageValue() + definition.damage();
                    if (damage >= tool.getMaxDamage()) continue;
                    tool.setDamageValue((int)damage);
                }
                remaining.set(slot, tool);
            }
        }
        return remaining;
    }

    @Override public NonNullList<Ingredient> getIngredients() {
        var result = NonNullList.<Ingredient>create(); result.add(input);
        slots.forEach(slot -> result.add(slot.ingredient())); return result;
    }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) {
        ItemStack[] items = input.getItems();
        return items.length == 0 ? ItemStack.EMPTY : upgrade(items[0]);
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= slots.size() + 1; }
    @Override public CraftingBookCategory category() { return CraftingBookCategory.EQUIPMENT; }
    // Dynamic component-sensitive output is displayed in the dedicated JEI category, like other special recipes.
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return EquipmentUpgrades.SERIALIZER.get(); }

    public static final class Serializer implements RecipeSerializer<EquipmentUpgradeRecipe> {
        private record Data(Ingredient input, ItemStack output, List<UpgradeIngredient> ingredients,
                            Holder<Enchantment> enchantment, int maxLevel, double durabilityMultiplier) {}
        private static final MapCodec<Data> DATA = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(Data::input),
                ItemStack.CODEC.optionalFieldOf("output", ItemStack.EMPTY).forGetter(Data::output),
                UpgradeIngredient.CODEC.listOf(1, 8).fieldOf("ingredients").forGetter(Data::ingredients),
                Enchantment.CODEC.fieldOf("enchantment").forGetter(Data::enchantment),
                Codec.intRange(1, 255).fieldOf("max_level").forGetter(Data::maxLevel),
                Codec.DOUBLE.fieldOf("durability_multiplier").forGetter(Data::durabilityMultiplier)
        ).apply(i, Data::new));
        private static final MapCodec<EquipmentUpgradeRecipe> CODEC = DATA.flatXmap(data -> {
            try { return DataResult.success(new EquipmentUpgradeRecipe(data.input, data.output, data.ingredients,
                    data.enchantment, data.maxLevel, data.durabilityMultiplier)); }
            catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
        }, recipe -> DataResult.success(new Data(recipe.input, recipe.output, recipe.ingredients, recipe.enchantment, recipe.maxLevel, recipe.durabilityMultiplier)));
        @Override public MapCodec<EquipmentUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, EquipmentUpgradeRecipe> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    }
}
