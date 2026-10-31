package net.quiwh.cwi.interaction;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.stream.Stream;

/** The wire format is the validated data definition, never a script callback. */
public final class InteractionRecipe implements Recipe<RecipeInput> {
    private final InteractionRecipes.Kind kind;
    private final JsonObject definition;
    private final InteractionRules.Rule rule;

    private InteractionRecipe(InteractionRecipes.Kind kind, JsonObject definition, InteractionRules.Rule rule) {
        this.kind = kind;
        this.definition = definition.deepCopy();
        this.definition.remove("_kubejs_changed_marker");
        this.definition.remove("type");
        this.rule = rule;
    }

    public InteractionRecipes.Kind kind() { return kind; }
    public InteractionRules.Rule rule() { return rule; }
    public JsonObject definition() { return definition.deepCopy(); }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return kind.serializer(); }
    @Override public RecipeType<?> getType() { return kind.type(); }

    public static final class Serializer implements RecipeSerializer<InteractionRecipe> {
        private final MapCodec<InteractionRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, InteractionRecipe> stream;

        Serializer(InteractionRecipes.Kind kind) {
            codec = new MapCodec<>() {
                @Override public <T> DataResult<InteractionRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
                    try {
                        JsonObject json = new JsonObject();
                        input.entries().forEach(pair -> json.add(ops.getStringValue(pair.getFirst()).getOrThrow(),
                                ops.convertTo(JsonOps.INSTANCE, pair.getSecond())));
                        return DataResult.success(new InteractionRecipe(kind, json, InteractionRules.decode(kind, json, ops)));
                    } catch (RuntimeException exception) {
                        return DataResult.error(() -> kind.id() + ": " + exception.getMessage());
                    }
                }
                @Override public <T> RecordBuilder<T> encode(InteractionRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    recipe.definition.entrySet().forEach(entry -> {
                        if (!entry.getKey().equals("type")) prefix.add(entry.getKey(), JsonOps.INSTANCE.convertTo(ops, entry.getValue()));
                    });
                    return prefix;
                }
                @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.empty(); }
            };
            stream = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        }
        @Override public MapCodec<InteractionRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, InteractionRecipe> streamCodec() { return stream; }
    }
}
