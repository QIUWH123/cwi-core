package net.quiwh.cwi.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.ItemWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.recipe.schema.*;
import dev.latvian.mods.kubejs.util.IntBounds;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import snownee.kiwi.recipe.SizedIngredient;
import snownee.lychee.util.action.PostAction;
import snownee.lychee.util.codec.LycheeCodecs;
import snownee.lychee.util.predicates.BlockPredicateExtensions;

/** Loaded only with Lychee; native codecs validate conditions, actions, predicates and counted ingredients. */
public final class CwiLycheeRecipeSchemas {
    private static final RecipeComponentType<SizedIngredient> INPUT = RecipeComponentType.unit(id("lychee_ingredient"), Input::new);
    private static final RecipeComponentType<SizedIngredient> OPTIONAL_INPUT = RecipeComponentType.unit(id("lychee_optional_ingredient"),
            type -> new Input(type, ExtraCodecs.optionalEmptyMap(LycheeCodecs.SIZED_INGREDIENT)
                    .xmap(value -> value.orElse(SizedIngredient.EMPTY),
                            value -> value.ingredient().isEmpty() ? Optional.empty() : Optional.of(value)), true));
    private static final RecipeComponentType<PostAction> ACTION = RecipeComponentType.unit(id("lychee_action"), Action::new);
    private static final RecipeComponentType<BlockPredicate> BLOCK = RecipeComponentType.unit(id("lychee_block_predicate"),
            type -> new NativeComponent<>(type, BlockPredicateExtensions.CODEC_FOR_TESTING));
    private static final RecipeComponentType<JsonElement> JSON = RecipeComponentType.unit(id("lychee_context"),
            type -> new NativeComponent<>(type, Codec.PASSTHROUGH.xmap(dynamic -> dynamic.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue(),
                    json -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, json))));

    public static void registerComponents(RecipeComponentTypeRegistry registry) {
        registry.register(INPUT); registry.register(OPTIONAL_INPUT); registry.register(ACTION); registry.register(BLOCK); registry.register(JSON);
    }
    public static void register(RecipeSchemaRegistry registry) {
        var output = ACTION.instance().asListOrSelf().withBounds(IntBounds.of(0, Integer.MAX_VALUE)).outputKey("post");
        var block = BLOCK.otherKey("block_in").optional(BlockPredicateExtensions.ANY);
        var common = new RecipeKey<?>[]{
                BooleanComponent.BOOLEAN.otherKey("hide_in_viewer").optional(false), BooleanComponent.BOOLEAN.otherKey("ghost").optional(false),
                StringComponent.STRING.otherKey("comment").defaultOptional(), StringComponent.ID.otherKey("group").optional("default"),
                JSON.otherKey("if").alt("contextual").defaultOptional(), JSON.otherKey("max_repeats").defaultOptional()};
        install(registry, "item_inside", schema(common, output, INPUT.instance().asListOrSelf().inputKey("item_in"), block,
                NumberComponent.NON_NEGATIVE_INT.otherKey("time").optional(0)));
        install(registry, "item_burning", schema(common, output, INPUT.inputKey("item_in")));
        install(registry, "block_interacting", schema(common, output,
                OPTIONAL_INPUT.instance().asListOrSelf().withBounds(IntBounds.of(1, 2)).inputKey("item_in"), block));
        install(registry, "dripstone_dripping", schema(common, output, BLOCK.otherKey("source_block"), BLOCK.otherKey("target_block")));
    }
    private static RecipeSchema schema(RecipeKey<?>[] common, RecipeKey<?>... keys) {
        var list = new java.util.ArrayList<>(List.of(keys)); list.addAll(List.of(common));
        return new RecipeSchema(java.util.Map.of(), list);
    }
    private static void install(RecipeSchemaRegistry registry, String name, RecipeSchema schema) {
        var existing = registry.namespace("lychee").get(name);
        if (existing == null || existing instanceof UnknownRecipeSchemaType) registry.namespace("lychee").register(name, schema);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("cwi", path); }

    private record NativeComponent<T>(RecipeComponentType<?> type, Codec<T> codec) implements RecipeComponent<T> {
        @Override public TypeInfo typeInfo() { return TypeInfo.OBJECT; }
        @Override public T wrap(RecipeScriptContext context, Object value) { return codec.parse(context.ops().json(), JsonUtils.of(context.cx(), value)).getOrThrow(); }
    }
    private record Input(RecipeComponentType<?> type, Codec<SizedIngredient> codec, boolean allowEmpty) implements RecipeComponent<SizedIngredient> {
        Input(RecipeComponentType<SizedIngredient> type) { this(type, LycheeCodecs.SIZED_INGREDIENT, false); }
        @Override public TypeInfo typeInfo() { return TypeInfo.OBJECT; }
        @Override public SizedIngredient wrap(RecipeScriptContext context, Object value) {
            value = Wrapper.unwrapped(value);
            if (value instanceof SizedIngredient input) return input;
            if (value instanceof java.util.Map<?, ?> || value instanceof JsonElement) return codec.parse(context.ops().json(), JsonUtils.of(context.cx(), value)).getOrThrow();
            var nativeInput = (allowEmpty ? SizedIngredientComponent.OPTIONAL_SIZED_INGREDIENT : SizedIngredientComponent.SIZED_INGREDIENT)
                    .instance().wrap(context, value);
            return new SizedIngredient(nativeInput.ingredient(), nativeInput.count());
        }
        @Override public boolean matches(RecipeMatchContext context, SizedIngredient value, ReplacementMatchInfo match) {
            return IngredientComponent.INGREDIENT.instance().matches(context, value.ingredient(), match);
        }
        @Override public SizedIngredient replace(RecipeScriptContext context, SizedIngredient value, ReplacementMatchInfo match, Object replacement) {
            if (!matches(context, value, match)) return value;
            Ingredient replaced = IngredientComponent.INGREDIENT.instance().replace(context, value.ingredient(), match, replacement);
            return new SizedIngredient(replaced, value.count());
        }
        @Override public boolean isEmpty(SizedIngredient value) { return value.ingredient().isEmpty(); }
    }
    private record Action(RecipeComponentType<?> type) implements RecipeComponent<PostAction> {
        @Override public Codec<PostAction> codec() { return PostAction.CODEC; }
        @Override public TypeInfo typeInfo() { return TypeInfo.OBJECT; }
        @Override public PostAction wrap(RecipeScriptContext context, Object value) {
            value = Wrapper.unwrapped(value);
            if (value instanceof PostAction action) return action;
            if (value instanceof ItemStack || value instanceof CharSequence) {
                var json = ItemStack.CODEC.encodeStart(context.ops().json(), ItemWrapper.wrap(context.cx(), value)).getOrThrow().getAsJsonObject();
                json.addProperty("type", "drop_item");
                return codec().parse(context.ops().json(), json).getOrThrow();
            }
            return codec().parse(context.ops().json(), JsonUtils.of(context.cx(), value)).getOrThrow();
        }
        @Override public boolean matches(RecipeMatchContext context, PostAction action, ReplacementMatchInfo match) {
            return action.getOutputItems().stream().anyMatch(stack -> ItemStackComponent.ITEM_STACK.instance().matches(context, stack, match));
        }
        @Override public PostAction replace(RecipeScriptContext context, PostAction action, ReplacementMatchInfo match, Object replacement) {
            if (!matches(context, action, match)) return action;
            var json = codec().encodeStart(context.ops().json(), action).getOrThrow().deepCopy();
            replaceDrops(context, json, match, replacement);
            return codec().parse(context.ops().json(), json).getOrThrow();
        }
        private static void replaceDrops(RecipeScriptContext context, JsonElement element, ReplacementMatchInfo match, Object replacement) {
            if (element.isJsonArray()) { element.getAsJsonArray().forEach(child -> replaceDrops(context, child, match, replacement)); return; }
            if (!element.isJsonObject()) return;
            JsonObject json = element.getAsJsonObject();
            if (json.has("type") && ResourceLocation.parse(json.get("type").getAsString()).getPath().equals("drop_item")) {
                var stack = LycheeCodecs.NONEMPTY_ITEM_STACK_MAP_CODEC.codec().parse(context.ops().json(), json).getOrThrow();
                if (ItemStackComponent.ITEM_STACK.instance().matches(context, stack, match)) {
                    var result = ItemWrapper.wrap(context.cx(), replacement).copyWithCount(stack.getCount());
                    var encoded = ItemStack.CODEC.encodeStart(context.ops().json(), result).getOrThrow().getAsJsonObject();
                    json.remove("id"); json.remove("count"); json.remove("components");
                    encoded.entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
                }
            } else {
                // Traverse the action tree, retaining all native conditions, nested branches and metadata.
                json.entrySet().forEach(entry -> replaceDrops(context, entry.getValue(), match, replacement));
            }
        }
    }
    private CwiLycheeRecipeSchemas() {}
}
