package net.quiwh.cwi.compat.kubejs;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
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
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Item outputs with native Create serialization, independent of KubeJS Create. */
public record CwiProcessingOutputComponent(RecipeComponentType<?> type) implements RecipeComponent<ProcessingOutput> {
    public static final RecipeComponentType<ProcessingOutput> TYPE = RecipeComponentType.unit(
            ResourceLocation.fromNamespaceAndPath("cwi", "processing_output"), CwiProcessingOutputComponent::new);

    @Override @SuppressWarnings("removal") public Codec<ProcessingOutput> codec() { return ProcessingOutput.CODEC; }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(ProcessingOutput.class); }

    @Override public boolean hasPriority(RecipeMatchContext context, Object value) {
        value = unwrap(value);
        return value instanceof ProcessingOutput || value instanceof Map<?, ?> map && map.containsKey("chance")
                || value instanceof JsonObject json && json.has("chance");
    }

    @Override public ProcessingOutput wrap(RecipeScriptContext context, Object value) {
        value = unwrap(value);
        if (value instanceof ProcessingOutput output) return output;
        if (value instanceof Map<?, ?> map && map.containsKey("stack")) {
            return new ProcessingOutput(ItemWrapper.wrap(context.cx(), map.get("stack")), chance(map.get("chance")));
        }
        if (value instanceof Map<?, ?> || value instanceof JsonObject) {
            JsonObject json = JsonUtils.objectOf(context.cx(), value).deepCopy();
            // Accept the historical item field as well as the 1.21 id field.
            if (!json.has("id") && json.has("item")) json.add("id", json.remove("item"));
            return codec().parse(context.ops().json(), json).getOrThrow();
        }
        return new ProcessingOutput(ItemWrapper.wrap(context.cx(), value), 1F);
    }

    @Override public boolean matches(RecipeMatchContext context, ProcessingOutput output, ReplacementMatchInfo match) {
        return ItemStackComponent.ITEM_STACK.instance().matches(context, output.getStack(), match);
    }

    @Override public ProcessingOutput replace(RecipeScriptContext context, ProcessingOutput output,
            ReplacementMatchInfo match, Object replacement) {
        if (!matches(context, output, match)) return output;
        replacement = unwrap(replacement);
        ProcessingOutput result = wrap(context, replacement);
        if (hasPriority(context, replacement)) return result;
        return new ProcessingOutput(result.getStack().copyWithCount(output.getStack().getCount()), output.getChance());
    }

    @Override public boolean isEmpty(ProcessingOutput output) { return output.getStack().isEmpty(); }
    @Override public void buildUniqueId(UniqueIdBuilder builder, ProcessingOutput output) {
        if (!isEmpty(output)) builder.append(BuiltInRegistries.ITEM.getKey(output.getStack().getItem()));
    }

    private static Object unwrap(Object value) {
        while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
        return value;
    }

    private static float chance(Object value) {
        return value == null ? 1F : ((Number) value).floatValue();
    }
}
