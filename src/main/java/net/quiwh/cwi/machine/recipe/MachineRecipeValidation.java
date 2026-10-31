package net.quiwh.cwi.machine.recipe;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Rejects recipe fields that the selected machine cannot physically execute. */
final class MachineRecipeValidation {
    static @Nullable String error(MachineRecipes.Entry entry, List<MachineRecipe.ItemInput> items,
                                  List<MachineRecipe.FluidInput> fluids, List<MachineRecipe.ItemOutput> outputs,
                                  List<MachineRecipe.FluidOutput> fluidOutputs, float rpm, int heat, HeatCondition condition) {
        if (entry == MachineRecipes.BLAST_FURNACE) {
            if (rpm != 0) return "Blast furnace recipes cannot require RPM: the furnace has no kinetic input";
            if (!outputs.isEmpty()) return "Blast furnace recipes support molten fluid outputs, not item outputs";
        } else if (heat != 0 || condition != HeatCondition.NONE) {
            return "Kinetic incubator and mixing vessel recipes cannot require heat";
        }
        if (entry == MachineRecipes.INCUBATING) {
            if (!fluids.isEmpty() || !fluidOutputs.isEmpty()) return "Incubator recipes cannot use fluids: the incubator has no tanks";
            if (items.size() != 1 || items.getFirst().count() != 1 || items.getFirst().perTick())
                return "Incubator recipes require exactly one ordinary single-item plate input";
        }
        return null;
    }

    private MachineRecipeValidation() {}
}
