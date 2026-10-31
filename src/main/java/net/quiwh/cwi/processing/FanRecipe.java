package net.quiwh.cwi.processing;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public final class FanRecipe extends StandardProcessingRecipe<SingleRecipeInput> {
    public FanRecipe(FanRecipes.Entry entry, ProcessingRecipeParams params) {
        super(entry, params);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.isEmpty() && !ingredients.isEmpty() && ingredients.getFirst().test(input.item());
    }

    @Override protected int getMaxInputCount() { return 1; }
    @Override protected int getMaxOutputCount() { return 12; }
    @Override protected boolean canSpecifyDuration() { return true; }
}
