package net.quiwh.cwi.machine.recipe;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Rejects recipe fields that the selected machine cannot physically execute. */
final class MachineRecipeValidation {
    static @Nullable String error(MachineRecipes.Entry entry, List<MachineRecipe.ItemInput> items,
                                  List<MachineRecipe.FluidInput> fluids, List<MachineRecipe.ItemOutput> outputs,
                                  List<MachineRecipe.FluidOutput> fluidOutputs, float rpm, int heat, int maximumHeat,
                                  HeatCondition condition) {
        if (entry == MachineRecipes.BLAST_FURNACE) {
            if (rpm != 0) return "Blast furnace recipes cannot require RPM: the furnace has no kinetic input";
            if (items.stream().anyMatch(input -> input.settings().tool()))
                return "Blast furnace recipes do not support durability or energy tool costs";
            int required = condition == HeatCondition.SUPERHEATED ? 1900 : condition == HeatCondition.HEATED ? 1200 : 0;
            if (maximumHeat < Math.max(required, heat)) return "Maximum furnace temperature cannot be below the required minimum temperature";
        } else if (heat != 0 || maximumHeat != Integer.MAX_VALUE || condition != HeatCondition.NONE) {
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
