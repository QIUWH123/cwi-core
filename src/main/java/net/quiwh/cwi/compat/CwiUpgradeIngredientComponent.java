package net.quiwh.cwi.compat;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.SizedIngredientWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.IngredientComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwi.equipment.UpgradeIngredient;

import java.util.Map;

/** Normal KubeJS ingredients/counts with explicit reusable-tool metadata. */
public record CwiUpgradeIngredientComponent(RecipeComponentType<?> type) implements RecipeComponent<UpgradeIngredient> {
    public static final RecipeComponentType<UpgradeIngredient> TYPE = RecipeComponentType.unit(
            ResourceLocation.parse("cwi:upgrade_ingredient"), CwiUpgradeIngredientComponent::new);
    @Override public Codec<UpgradeIngredient> codec() { return UpgradeIngredient.CODEC; }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(UpgradeIngredient.class); }
    @Override public UpgradeIngredient wrap(RecipeScriptContext context, Object value) { return wrap(context.cx(), value); }
    public static UpgradeIngredient wrap(Context cx, Object value) {
        value = Wrapper.unwrapped(value);
        if (value instanceof UpgradeIngredient ingredient) return ingredient;
        if (value instanceof Map<?, ?> map) {
            Object raw = map.containsKey("ingredient") ? map.get("ingredient") : map.containsKey("item") ? map.get("item") : map.get("id");
            if (raw != null) {
                var sized = SizedIngredientWrapper.wrap(cx, raw);
                int count = map.containsKey("count") ? integer(map.get("count")) : sized.count();
                boolean consume = !Boolean.FALSE.equals(map.get("consume"));
                int damage = map.containsKey("damage") ? integer(map.get("damage")) : 1;
                return new UpgradeIngredient(sized.ingredient(), count, consume, damage);
            }
        }
        var sized = SizedIngredientWrapper.wrap(cx, value);
        return new UpgradeIngredient(sized.ingredient(), sized.count(), true, 1);
    }
    private static int integer(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.intValue())
            throw new IllegalArgumentException("Upgrade count/damage must be an integer");
        return number.intValue();
    }
    @Override public boolean matches(RecipeMatchContext context, UpgradeIngredient value, ReplacementMatchInfo match) {
        return IngredientComponent.INGREDIENT.instance().matches(context, value.ingredient(), match);
    }
    @Override public UpgradeIngredient replace(RecipeScriptContext context, UpgradeIngredient value, ReplacementMatchInfo match, Object replacement) {
        if (!matches(context, value, match)) return value;
        // A normal replacement changes the ingredient, preserving the crafting slot count and tool policy.
        var changed = SizedIngredientWrapper.wrap(context.cx(), replacement);
        return new UpgradeIngredient(changed.ingredient(), value.count(), value.consume(), value.damage());
    }
    @Override public boolean isEmpty(UpgradeIngredient value) { return value.ingredient().isEmpty(); }
    @Override public void buildUniqueId(UniqueIdBuilder builder, UpgradeIngredient value) {
        IngredientComponent.INGREDIENT.instance().buildUniqueId(builder, value.ingredient());
    }
}
