package net.quiwh.cwi.mixin.client;

import com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugationRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.compat.jei.category.CentrifugationCategory", remap = false)
abstract class CentrifugationCategoryMixin {
    @Inject(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/negodya1/vintageimprovements/content/kinetics/centrifuge/CentrifugationRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At("HEAD"), cancellable = true)
    private void cwi$expandedOutputs(IRecipeLayoutBuilder builder, CentrifugationRecipe recipe, IFocusGroup focuses, CallbackInfo ci) {
        if (recipe.getRollableResults().size() <= 4) return;
        int index = 0;
        for (var input : recipe.getIngredients()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10 + index % 3 * 19, 5 + index / 3 * 19)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1).addIngredients(input);
            index++;
        }
        for (var input : recipe.getFluidIngredients()) {
            CreateRecipeCategory.addFluidSlot(builder, 10 + index % 3 * 19, 5 + index / 3 * 19, input);
            index++;
        }
        int rows = (recipe.getRollableResults().size() + recipe.getFluidResults().size() + 2) / 3;
        int top = 94 - rows * 19;
        index = 0;
        for (var output : recipe.getRollableResults()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 112 + index % 3 * 19, top + index / 3 * 19)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(output), -1, -1).addItemStack(output.getStack())
                    .addRichTooltipCallback(CreateRecipeCategory.addStochasticTooltip(output));
            index++;
        }
        for (var output : recipe.getFluidResults()) {
            CreateRecipeCategory.addFluidSlot(builder, 112 + index % 3 * 19, top + index / 3 * 19, output);
            index++;
        }
        ci.cancel();
    }

    @ModifyArgs(method = "draw(Lcom/negodya1/vintageimprovements/content/kinetics/centrifuge/CentrifugationRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V", ordinal = 1))
    private void cwi$moveOutputArrow(Args args, CentrifugationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (recipe.getRollableResults().size() > 4) args.set(1, 94);
    }
}
