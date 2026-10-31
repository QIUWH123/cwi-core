package net.quiwh.cwi.compat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.ItemWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.hammer.HammerRecipe;

import java.util.Map;

/** KubeJS wrapper for hammer drop stacks with Create-style chance handling. */
public final class CwiHammerRecipeComponents {
    public static final RecipeComponentType<HammerRecipe.Drop> DROP = RecipeComponentType.unit(
            ResourceLocation.fromNamespaceAndPath("cwi", "hammer_drop"), DropComponent::new);

    public static void register(dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry registry) {
        registry.register(DROP);
    }

    private record DropComponent(RecipeComponentType<?> type) implements RecipeComponent<HammerRecipe.Drop> {
        @Override public Codec<HammerRecipe.Drop> codec() { return HammerRecipe.Drop.CODEC; }
        @Override public TypeInfo typeInfo() { return TypeInfo.of(HammerRecipe.Drop.class); }

        @Override public HammerRecipe.Drop wrap(RecipeScriptContext context, Object value) {
            value = unwrap(value);
            if (value instanceof HammerRecipe.Drop drop) return drop;
            if (value instanceof Map<?, ?> map && map.containsKey("stack")) {
                ItemStack stack = ItemWrapper.wrap(context.cx(), map.get("stack"));
                if (map.containsKey("count")) stack.setCount(positiveCount(map.get("count")));
                return new HammerRecipe.Drop(stack, number(map.get("chance")));
            }
            if (value instanceof Map<?, ?> || value instanceof JsonObject) {
                JsonObject json = JsonUtils.objectOf(context.cx(), value).deepCopy();
                float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1F;
                json.remove("chance");
                if (!json.has("id") && json.has("item")) json.add("id", json.remove("item"));
                return new HammerRecipe.Drop(ItemStack.CODEC.parse(context.ops().json(), json).getOrThrow(), chance);
            }
            return new HammerRecipe.Drop(ItemWrapper.wrap(context.cx(), value), 1F);
        }

        @Override public boolean matches(RecipeMatchContext context, HammerRecipe.Drop value, ReplacementMatchInfo match) {
            return ItemStackComponent.ITEM_STACK.instance().matches(context, value.stack(), match);
        }

        @Override public HammerRecipe.Drop replace(RecipeScriptContext context, HammerRecipe.Drop value,
                                                    ReplacementMatchInfo match, Object replacement) {
            if (!matches(context, value, match)) return value;
            Object raw = unwrap(replacement);
            if (raw instanceof Map<?, ?> || raw instanceof JsonObject || raw instanceof HammerRecipe.Drop)
                return wrap(context, raw);
            return new HammerRecipe.Drop(ItemWrapper.wrap(context.cx(), raw).copyWithCount(value.stack().getCount()), value.chance());
        }

        @Override public boolean isEmpty(HammerRecipe.Drop value) { return value.stack().isEmpty(); }
        @Override public void buildUniqueId(UniqueIdBuilder builder, HammerRecipe.Drop value) {
            ItemStackComponent.ITEM_STACK.instance().buildUniqueId(builder, value.stack());
        }

        private static float number(Object value) { return value == null ? 1F : ((Number) value).floatValue(); }
        private static int positiveCount(Object value) {
            double count = ((Number) value).doubleValue();
            if (!Double.isFinite(count) || count != Math.rint(count) || count <= 0 || count > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Hammer drop count must be a positive integer");
            return (int) count;
        }
        private static Object unwrap(Object value) { while (value instanceof Wrapper wrapper) value = wrapper.unwrap(); return value; }
    }

    private CwiHammerRecipeComponents() {}
}
