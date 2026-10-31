package net.quiwh.cwi.processing;

import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FanRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "cwi");
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "cwi");
    public static final Entry CORRODING = new Entry(FanMode.CORRODING);
    public static final Entry STERILIZING = new Entry(FanMode.STERILIZING);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }

    public static final class Entry implements IRecipeTypeInfo {
        private final FanMode mode;
        private final DeferredHolder<RecipeType<?>, RecipeType<FanRecipe>> type;
        private final DeferredHolder<RecipeSerializer<?>, StandardProcessingRecipe.Serializer<FanRecipe>> serializer;

        private Entry(FanMode mode) {
            this.mode = mode;
            type = TYPES.register(mode.recipeId().getPath(), () -> new RecipeType<>() {
                @Override public String toString() { return mode.recipeId().toString(); }
            });
            serializer = SERIALIZERS.register(mode.recipeId().getPath(),
                    () -> new StandardProcessingRecipe.Serializer<>(params -> new FanRecipe(this, params)));
        }

        public FanMode mode() { return mode; }
        public RecipeType<FanRecipe> recipeType() { return type.get(); }
        @Override public ResourceLocation getId() { return mode.recipeId(); }
        @Override @SuppressWarnings("unchecked")
        public <T extends RecipeSerializer<?>> T getSerializer() { return (T) serializer.get(); }
        @Override @SuppressWarnings("unchecked")
        public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
            return (RecipeType<R>) type.get();
        }
    }

    private FanRecipes() {}
}
