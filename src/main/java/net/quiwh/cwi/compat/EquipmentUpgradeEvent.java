package net.quiwh.cwi.compat;

import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.quiwh.cwi.equipment.EquipmentUpgradeRecipe;
import net.quiwh.cwi.equipment.UpgradeIngredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fresh, per-resource-reload material and upgrade definitions. No global client/server state. */
public final class EquipmentUpgradeEvent implements KubeEvent {
    public static final EventGroup GROUP = CwiKubeJSEvents.GROUP;
    public static final EventHandler EVENT = CwiKubeJSEvents.EQUIPMENT_UPGRADES;
    private final Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
    private final Map<ResourceLocation, Upgrade> upgrades = new LinkedHashMap<>();

    public Material material(String id) {
        var key = id(id);
        if (materials.containsKey(key)) throw new IllegalArgumentException("Duplicate upgrade material: " + key);
        var material = new Material(); materials.put(key, material); return material;
    }
    public Upgrade upgrade(String id) {
        var key = id(id);
        if (upgrades.containsKey(key)) throw new IllegalArgumentException("Duplicate equipment upgrade: " + key);
        var upgrade = new Upgrade(); upgrades.put(key, upgrade); return upgrade;
    }
    private static ResourceLocation id(String id) { return ResourceLocation.parse(id.contains(":") ? id : "cwi:" + id); }
    private static String name(String name) {
        if (!ResourceLocation.isValidPath(name) || name.isEmpty()) throw new IllegalArgumentException("Invalid equipment/component name: " + name);
        return name;
    }

    public static final class Material {
        private int tier = 1;
        private double multiplier = 1;
        private final Map<String, Ingredient> equipment = new LinkedHashMap<>();
        private final Map<String, Ingredient> components = new LinkedHashMap<>();
        public Material tier(int tier) {
            if (tier < 1) throw new IllegalArgumentException("Material tier must be positive");
            this.tier = tier; return this;
        }
        public Material durabilityMultiplier(double multiplier) {
            if (!Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("Invalid material durability multiplier");
            this.multiplier = multiplier; return this;
        }
        public Material equipment(String type, Ingredient item) { equipment.put(name(type), nonempty(item)); return this; }
        public Material component(String type, Ingredient ingredient) { components.put(name(type), nonempty(ingredient)); return this; }
    }

    private record Part(UpgradeIngredient fixed, String component, int count, boolean consume, int damage) {
        UpgradeIngredient resolve(Material material) {
            if (fixed != null) return fixed;
            if (material == null || !material.components.containsKey(component))
                throw new IllegalArgumentException("Missing material component '" + component + "'");
            return new UpgradeIngredient(material.components.get(component), count, consume, damage);
        }
    }
    public static final class Upgrade {
        private ResourceLocation enchantment;
        private int maxLevel = 1, minimumTier = 1;
        private double cost = 0;
        private boolean fixedMax;
        private Ingredient input;
        private ItemStack output = ItemStack.EMPTY;
        private final Set<String> types = new LinkedHashSet<>();
        private final Set<ResourceLocation> selected = new LinkedHashSet<>();
        private final List<Part> parts = new ArrayList<>();
        public Upgrade enchantment(String id) { enchantment = ResourceLocation.parse(id); return this; }
        public Upgrade maxLevel(int level) {
            if (level < 1 || level > 255) throw new IllegalArgumentException("Upgrade cap must be in [1,255]");
            maxLevel = level; return this;
        }
        public Upgrade minimumTier(int tier) {
            if (tier < 1) throw new IllegalArgumentException("Minimum tier must be positive");
            minimumTier = tier; return this;
        }
        public Upgrade fixedMax(boolean fixed) { fixedMax = fixed; return this; }
        public Upgrade durabilityCost(double cost) {
            if (!Double.isFinite(cost) || cost < 0) throw new IllegalArgumentException("Invalid durability cost");
            this.cost = cost; return this;
        }
        public Upgrade types(String... types) { for (var type : types) this.types.add(name(type)); return this; }
        public Upgrade materials(String... ids) { for (var id : ids) selected.add(id(id)); return this; }
        public Upgrade input(Ingredient input) { this.input = nonempty(input); return this; }
        public Upgrade output(ItemStack output) {
            if (output.isEmpty() || output.getCount() != 1) throw new IllegalArgumentException("Upgrade output must be one item");
            this.output = output.copy(); return this;
        }
        public Upgrade ingredient(Ingredient ingredient, int count) { return add(new UpgradeIngredient(ingredient, count, true, 1)); }
        public Upgrade tool(Ingredient ingredient, int damage) { return add(new UpgradeIngredient(ingredient, 1, false, damage)); }
        public Upgrade ingredients(Context cx, Object[] ingredients) {
            for (var value : ingredients) add(CwiUpgradeIngredientComponent.wrap(cx, value)); return this;
        }
        public Upgrade component(String name, int count, boolean consume, int damage) {
            if (count < 1 || count > 8 || damage < 0) throw new IllegalArgumentException("Invalid material ingredient count/damage");
            parts.add(new Part(null, name(name), count, consume, damage)); return this;
        }
        private Upgrade add(UpgradeIngredient ingredient) { parts.add(new Part(ingredient, null, 0, true, 0)); return this; }
    }
    private static Ingredient nonempty(Ingredient input) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("Equipment upgrade ingredients cannot be empty");
        return input;
    }

    @HideFromJS
    public void generate(RecipesKubeEvent event, Map<ResourceLocation, JsonElement> recipes) {
        // Validate everything before changing the recipe discovery map.
        Map<ResourceLocation, JsonElement> generated = new LinkedHashMap<>();
        upgrades.forEach((ruleId, rule) -> {
            if (rule.enchantment == null) throw new IllegalArgumentException("Upgrade needs an enchantment: " + ruleId);
            var enchantment = event.registries.access().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, rule.enchantment));
            for (var key : rule.selected) if (!materials.containsKey(key)) throw new IllegalArgumentException("Unknown upgrade material: " + key);
            if (rule.input != null && rule.types.isEmpty() && rule.selected.isEmpty()) {
                add(event, recipes, generated, ruleId.withPath("equipment_upgrade/" + ruleId.getPath() + "_manual_only"),
                        rule, null, rule.input, enchantment);
            } else {
                if (rule.input == null && rule.types.isEmpty()) throw new IllegalArgumentException("Upgrade needs input or equipment types: " + ruleId);
                materials.forEach((materialId, material) -> {
                    if ((!rule.selected.isEmpty() && !rule.selected.contains(materialId)) || material.tier < rule.minimumTier) return;
                    if (rule.input != null) {
                        add(event, recipes, generated, expandedId(ruleId, materialId, "direct"), rule, material, rule.input, enchantment);
                    } else for (var type : rule.types) {
                        var input = material.equipment.get(type);
                        if (input != null) add(event, recipes, generated, expandedId(ruleId, materialId, type), rule, material, input, enchantment);
                    }
                });
            }
        });
        recipes.putAll(generated);
    }
    private static ResourceLocation expandedId(ResourceLocation rule, ResourceLocation material, String type) {
        return rule.withPath("equipment_upgrade/" + rule.getPath() + "/" + material.getNamespace() + "/" + material.getPath() + "/" + type + "_manual_only");
    }
    private static void add(RecipesKubeEvent event, Map<ResourceLocation, JsonElement> existing, Map<ResourceLocation, JsonElement> generated,
                            ResourceLocation id, Upgrade rule, Material material, Ingredient input,
                            net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        if (existing.containsKey(id) || generated.containsKey(id)) throw new IllegalArgumentException("Duplicate generated upgrade recipe: " + id);
        int cap = material == null || rule.fixedMax ? rule.maxLevel : Math.min(rule.maxLevel, material.tier);
        var ingredients = rule.parts.stream().map(part -> part.resolve(material)).toList();
        var recipe = new EquipmentUpgradeRecipe(input, rule.output, ingredients, enchantment, cap, rule.cost * (material == null ? 1 : material.multiplier));
        generated.put(id, Recipe.CODEC.encodeStart(event.ops.json(), recipe).getOrThrow());
    }
}
