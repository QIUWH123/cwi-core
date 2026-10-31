package net.quiwh.cwi.hammer;

import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Native recipe types used by CWI's stage-charged hammer conversions. */
public final class HammerRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "cwi");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "cwi");
    public static final Entry BLOCK_CONVERSION = new Entry("block_conversion");
    public static final Entry DEPOT_CONVERSION = new Entry("depot_conversion");

    public static List<Entry> entries() { return List.of(BLOCK_CONVERSION, DEPOT_CONVERSION); }
    public static void register(IEventBus bus) { TYPES.register(bus); SERIALIZERS.register(bus); }

    public static Optional<RecipeHolder<HammerRecipe>> findBlock(Level level, ItemStack input, int hammerStage) {
        return find(level, BLOCK_CONVERSION, input, hammerStage);
    }

    public static Optional<RecipeHolder<HammerRecipe>> findBlock(Level level, ItemStack input) {
        return findBlock(level, input, Integer.MAX_VALUE);
    }

    public static Optional<RecipeHolder<HammerRecipe>> findDepot(Level level, ItemStack input, int hammerStage) {
        return find(level, DEPOT_CONVERSION, input, hammerStage);
    }

    public static Optional<RecipeHolder<HammerRecipe>> findDepot(Level level, ItemStack input) {
        return findDepot(level, input, Integer.MAX_VALUE);
    }

    private static Optional<RecipeHolder<HammerRecipe>> find(Level level, Entry entry, ItemStack input, int hammerStage) {
        if (level == null || input == null || input.isEmpty() || hammerStage <= 0) return Optional.empty();
        return level.getRecipeManager().getAllRecipesFor(entry.recipeType()).stream()
                .filter(holder -> holder.value().stage() <= hammerStage && holder.value().matches(input))
                .max(Comparator.comparingInt((RecipeHolder<HammerRecipe> holder) -> holder.value().stage())
                        .thenComparing(holder -> holder.id().toString()));
    }

    public static final class Entry implements IRecipeTypeInfo {
        private final ResourceLocation id;
        private final DeferredHolder<RecipeType<?>, RecipeType<HammerRecipe>> type;
        private final DeferredHolder<RecipeSerializer<?>, HammerRecipe.Serializer> serializer;

        private Entry(String path) {
            id = ResourceLocation.fromNamespaceAndPath("cwi", path);
            type = TYPES.register(path, () -> new RecipeType<>() {
                @Override public String toString() { return id.toString(); }
            });
            serializer = SERIALIZERS.register(path, () -> new HammerRecipe.Serializer(this));
        }

        public ResourceLocation id() { return id; }
        public RecipeType<HammerRecipe> recipeType() { return type.get(); }
        public HammerRecipe.Serializer serializer() { return serializer.get(); }
        @Override public ResourceLocation getId() { return id; }
        @Override @SuppressWarnings("unchecked") public <T extends RecipeSerializer<?>> T getSerializer() { return (T) serializer.get(); }
        @Override @SuppressWarnings("unchecked") public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
            return (RecipeType<R>) type.get();
        }
    }

    private HammerRecipes() {}
}
