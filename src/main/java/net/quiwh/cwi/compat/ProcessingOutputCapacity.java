package net.quiwh.cwi.compat;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Simulates a complete output batch in one private inventory, reserving space cumulatively. */
public final class ProcessingOutputCapacity {
    public static boolean canFit(IItemHandler inventory, List<ItemStack> outputs) {
        var copy = new ItemStackHandler(inventory.getSlots()) {
            @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }
        };
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            copy.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        for (var output : outputs)
            if (!ItemHandlerHelper.insertItemStacked(copy, output.copy(), false).isEmpty()) return false;
        return true;
    }

    /** Direct-processing machines discard insertion remainders; reserve every possible result before consumption. */
    public static boolean canFitRecipe(IItemHandler inventory, ProcessingRecipe<?, ?> recipe) {
        return canFit(inventory, recipe.getRollableResults().stream().filter(output -> output.getChance() > 0)
                .map(output -> output.getStack()).toList());
    }

    private ProcessingOutputCapacity() { }
}
