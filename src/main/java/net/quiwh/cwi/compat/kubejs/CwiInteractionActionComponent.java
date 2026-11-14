package net.quiwh.cwi.compat.kubejs;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/** Data-only impact actions are validated by the destination native codec. */
public record CwiInteractionActionComponent(RecipeComponentType<?> type) implements RecipeComponent<JsonElement> {
    public static final RecipeComponentType<JsonElement> TYPE = RecipeComponentType.unit(
            ResourceLocation.parse("cwi:impact_action"), CwiInteractionActionComponent::new);
    public static final RecipeComponentType<JsonElement> EFFECT = RecipeComponentType.unit(
            ResourceLocation.parse("cwi:hazard_effect"), CwiInteractionActionComponent::new);
    @Override public Codec<JsonElement> codec() { return ExtraCodecs.JSON; }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(java.util.Map.class); }
    @Override public JsonElement wrap(RecipeScriptContext context, Object value) { return JsonUtils.of(context.cx(), value).deepCopy(); }
}
