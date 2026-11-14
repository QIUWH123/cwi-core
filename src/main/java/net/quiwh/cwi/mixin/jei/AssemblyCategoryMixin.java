package net.quiwh.cwi.mixin.jei;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.SequencedAssemblyCategory;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.compat.AssemblyOutputVisibility;
import net.quiwh.cwi.compat.jei.ProcessingChance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SequencedAssemblyCategory.class, remap = false)
abstract class AssemblyCategoryMixin {
    @Inject(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/simibubi/create/content/processing/sequenced/SequencedAssemblyRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V", at = @At("TAIL"))
    private void cwi$visibleWaste(IRecipeLayoutBuilder builder, SequencedAssemblyRecipe recipe, IFocusGroup focus, CallbackInfo ci) {
        if (!((AssemblyOutputVisibility) recipe).cwi$showOutputs() || recipe.resultPool.size() < 2 || recipe.getOutputChance() == 1) return;
        double total = recipe.resultPool.stream().mapToDouble(output -> output.getChance()).sum();
        builder.addSlot(RecipeIngredientRole.OUTPUT, 144, 91).setSlotName("cwi_assembly_waste")
                .setBackground(CreateRecipeCategory.getRenderedSlot(0f), -1, -1)
                .addItemStacks(recipe.resultPool.subList(1, recipe.resultPool.size()).stream().map(output -> output.getStack()).toList())
                .addRichTooltipCallback((view, tooltip) -> view.getDisplayedItemStack().ifPresent(stack -> {
                    double weight = recipe.resultPool.subList(1, recipe.resultPool.size()).stream()
                            .filter(output -> ItemStack.matches(output.getStack(), stack)).mapToDouble(output -> output.getChance()).sum();
                    tooltip.add(ProcessingChance.tooltip(weight / total));
                }));
    }

    @Definition(id = "singleOutput", local = @Local(name = "singleOutput", type = boolean.class))
    @Expression("singleOutput")
    @ModifyExpressionValue(method = "draw(Lcom/simibubi/create/content/processing/sequenced/SequencedAssemblyRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 1))
    private boolean cwi$hideQuestionMark(boolean single, @Local(argsOnly = true) SequencedAssemblyRecipe recipe) {
        return single || ((AssemblyOutputVisibility) recipe).cwi$showOutputs();
    }

    @Definition(id = "singleOutput", local = @Local(name = "singleOutput", type = boolean.class))
    @Expression("singleOutput")
    @ModifyExpressionValue(method = "getTooltipStrings(Lcom/simibubi/create/content/processing/sequenced/SequencedAssemblyRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;DD)Ljava/util/List;",
            at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean cwi$hideSalvageTooltip(boolean single, @Local(argsOnly = true) SequencedAssemblyRecipe recipe) {
        return single || ((AssemblyOutputVisibility) recipe).cwi$showOutputs();
    }
}
