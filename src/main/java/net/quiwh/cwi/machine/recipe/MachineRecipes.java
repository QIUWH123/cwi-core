package net.quiwh.cwi.machine.recipe;

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

import java.util.List;

/** Native recipe-manager types: datapack reload and login sync use the same serializers. */
public final class MachineRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "cwi");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "cwi");
    public static final Entry BLAST_FURNACE = new Entry("blast_furnace_processing", "blast_furnace");
    public static final Entry INCUBATING = new Entry("incubating", "incubator");
    public static final Entry MIXING = new Entry("mixing_vessel_mixing", "mixing_vessel");

    public static List<Entry> entries() { return List.of(BLAST_FURNACE, INCUBATING, MIXING); }
    public static void register(IEventBus bus) { TYPES.register(bus); SERIALIZERS.register(bus); }

    public static final class Entry implements IRecipeTypeInfo {
        private final ResourceLocation id;
        private final ResourceLocation machine;
        private final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> type;
        private final DeferredHolder<RecipeSerializer<?>, MachineRecipe.Serializer> serializer;

        private Entry(String path, String machine) {
            id = ResourceLocation.fromNamespaceAndPath("cwi", path);
            this.machine = ResourceLocation.fromNamespaceAndPath("cwi", machine);
            type = TYPES.register(path, () -> new RecipeType<>() {
                @Override public String toString() { return id.toString(); }
            });
            serializer = SERIALIZERS.register(path, () -> new MachineRecipe.Serializer(this));
        }

        public ResourceLocation machineId() { return machine; }
        public RecipeType<MachineRecipe> recipeType() { return type.get(); }
        public MachineRecipe.Serializer serializer() { return serializer.get(); }
        @Override public ResourceLocation getId() { return id; }
        @Override @SuppressWarnings("unchecked")
        public <T extends RecipeSerializer<?>> T getSerializer() { return (T) serializer.get(); }
        @Override @SuppressWarnings("unchecked")
        public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() { return (RecipeType<R>) type.get(); }
    }

    private MachineRecipes() {}
}
