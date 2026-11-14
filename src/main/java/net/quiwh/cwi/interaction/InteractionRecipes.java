package net.quiwh.cwi.interaction;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.item.crafting.RecipeManager;

/** Reloadable native rules; a level always reads its own recipe manager. */
public final class InteractionRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "cwi");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "cwi");
    private static final Map<RecipeManager, Index> CACHE = new WeakHashMap<>();
    private static final class Index {
        final Collection<RecipeHolder<?>> source;
        final EnumMap<Kind, List<RecipeHolder<InteractionRecipe>>> rules = new EnumMap<>(Kind.class);
        RegistryAccess fluidRegistries;
        FluidHazards fluidHazards;

        Index(Collection<RecipeHolder<?>> source) { this.source = source; }
    }

    public enum Kind {
        ITEM_IMPACT("item_impact"), INVENTORY_HAZARD("inventory_hazard"), FLUID_HAZARD("fluid_hazard"),
        TOXIN_EXPOSURE("toxin_exposure"), LOCKPICKING("lockpicking"), BLOCK_SPOUTING("block_spouting"),
        BUDDING_CATALYSIS("budding_catalysis"), DRYING("drying");

        private final ResourceLocation id;
        private final DeferredHolder<RecipeType<?>, RecipeType<InteractionRecipe>> type;
        private final DeferredHolder<RecipeSerializer<?>, InteractionRecipe.Serializer> serializer;

        Kind(String path) {
            id = ResourceLocation.fromNamespaceAndPath("cwi", path);
            type = TYPES.register(path, () -> new RecipeType<>() {
                @Override public String toString() { return id.toString(); }
            });
            serializer = SERIALIZERS.register(path, () -> new InteractionRecipe.Serializer(this));
        }
        public ResourceLocation id() { return id; }
        public RecipeType<InteractionRecipe> type() { return type.get(); }
        public InteractionRecipe.Serializer serializer() { return serializer.get(); }
    }

    public static void register(IEventBus bus) {
        Kind.values();
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }

    public static synchronized List<RecipeHolder<InteractionRecipe>> rules(Level level, Kind kind) {
        return rules(level.getRecipeManager(), kind);
    }

    private static List<RecipeHolder<InteractionRecipe>> rules(RecipeManager manager, Kind kind) {
        return index(manager).rules.computeIfAbsent(kind, key -> manager.getAllRecipesFor(kind.type()).stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList());
    }

    static synchronized FluidHazards fluidHazards(RecipeManager manager, RegistryAccess registries) {
        var index = index(manager);
        if (index.fluidHazards == null || index.fluidRegistries != registries) {
            index.fluidHazards = FluidHazards.compile(rules(manager, Kind.FLUID_HAZARD), registries);
            index.fluidRegistries = registries;
        }
        return index.fluidHazards;
    }

    private static Index index(RecipeManager manager) {
        // Both native reload and replaceRecipes replace the immutable recipe map.
        // A tag event alone does not cover direct same-ID updates or removals.
        var source = manager.getRecipes();
        var index = CACHE.get(manager);
        if (index == null || index.source != source) {
            index = new Index(source);
            CACHE.put(manager, index);
        }
        return index;
    }

    public static synchronized void invalidate() { CACHE.clear(); }

    private InteractionRecipes() {}
}
