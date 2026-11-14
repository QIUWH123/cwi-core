package net.quiwh.cwi.machine;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.quiwh.cwi.machine.recipe.MachineRecipe;
import net.quiwh.cwi.machine.recipe.MachineRecipes;

/** Shared immutable ordering; never retains a server/recipe manager after it closes. */
public final class MachineRecipeIndex {
    private static final Map<RecipeManager, Index> INDEXES = new WeakHashMap<>();
    private record Index(Collection<RecipeHolder<?>> source, Map<RecipeType<MachineRecipe>, List<RecipeHolder<MachineRecipe>>> types) {}

    public static synchronized void invalidate() { INDEXES.clear(); }

    public static synchronized List<RecipeHolder<MachineRecipe>> sorted(RecipeManager manager, RecipeType<MachineRecipe> type) {
        // In native 1.21.1 these are the immutable byName map's cached values. Both apply()
        // and replaceRecipes() replace that map, including same-ID/content replacements.
        var source = manager.getRecipes();
        Index index = INDEXES.get(manager);
        if (index == null || index.source() != source) {
            index = new Index(source, new HashMap<>());
            INDEXES.put(manager, index);
        }
        return index.types().computeIfAbsent(type, key -> {
            Comparator<RecipeHolder<MachineRecipe>> order = Comparator.comparing(holder -> holder.id().toString());
            if (key == MachineRecipes.MIXING.recipeType()) order = Comparator
                    .<RecipeHolder<MachineRecipe>>comparingLong(holder -> holder.value().itemInputs().stream()
                            .mapToLong(MachineRecipe.ItemInput::count).sum()).reversed().thenComparing(order);
            if (key == MachineRecipes.MIXING.recipeType() || key == MachineRecipes.BLAST_FURNACE.recipeType()) order = Comparator
                    .<RecipeHolder<MachineRecipe>>comparingInt(holder -> holder.value().priority()).reversed().thenComparing(order);
            return manager.getAllRecipesFor(key).stream().sorted(order).toList();
        });
    }
    private MachineRecipeIndex() {}
}
