package net.quiwh.cwi.machine;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.quiwh.cwi.machine.recipe.MachineRecipe;

/** For fixed native machine handlers only. Compare contents, including mutations bypassing callbacks. */
final class RecipeCandidates {
    private final Predicate<MachineRecipe> accepts;
    private List<RecipeHolder<MachineRecipe>> source, matches;
    private ItemStack[] items = new ItemStack[0];
    private FluidStack[] fluids = new FluidStack[0];

    RecipeCandidates() { this(recipe -> true); }
    RecipeCandidates(Predicate<MachineRecipe> accepts) { this.accepts = accepts; }

    List<RecipeHolder<MachineRecipe>> find(RecipeManager manager, RecipeType<MachineRecipe> type,
            IItemHandler inventory, IFluidHandler tanks) {
        var recipes = MachineRecipeIndex.sorted(manager, type);
        if (source == recipes && unchanged(inventory, tanks)) return matches;
        source = recipes;
        items = new ItemStack[inventory.getSlots()];
        for (int i = 0; i < items.length; i++) items[i] = inventory.getStackInSlot(i).copy();
        fluids = new FluidStack[tanks == null ? 0 : tanks.getTanks()];
        for (int i = 0; i < fluids.length; i++) fluids[i] = tanks.getFluidInTank(i).copy();
        return matches = recipes.stream().filter(holder -> accepts.test(holder.value())
                && ((type == net.quiwh.cwi.machine.recipe.MachineRecipes.MIXING.recipeType()
                    || type == net.quiwh.cwi.machine.recipe.MachineRecipes.BLAST_FURNACE.recipeType())
                    ? VesselTransaction.eligible(holder.value(), (net.neoforged.neoforge.items.IItemHandlerModifiable)inventory,
                            (MutableRecipeFluids)tanks, null)
                    : RecipeTransaction.matches(holder.value(), inventory, tanks, 1))).toList();
    }

    private boolean unchanged(IItemHandler inventory, IFluidHandler tanks) {
        if (items.length != inventory.getSlots() || fluids.length != (tanks == null ? 0 : tanks.getTanks())) return false;
        for (int i = 0; i < items.length; i++) if (!ItemStack.matches(items[i], inventory.getStackInSlot(i))) return false;
        for (int i = 0; i < fluids.length; i++) {
            var current = tanks.getFluidInTank(i);
            if (fluids[i].getAmount() != current.getAmount() || !FluidStack.isSameFluidSameComponents(fluids[i], current)) return false;
        }
        return true;
    }
}
