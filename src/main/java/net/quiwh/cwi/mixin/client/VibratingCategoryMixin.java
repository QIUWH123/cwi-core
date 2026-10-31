package net.quiwh.cwi.mixin.client;

import com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.compat.jei.category.VibratingCategory", remap = false)
abstract class VibratingCategoryMixin {
    @Inject(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/negodya1/vintageimprovements/content/kinetics/vibration/VibratingRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At("HEAD"), cancellable = true)
    private void cwi$expandedOutputs(IRecipeLayoutBuilder builder, VibratingRecipe recipe, IFocusGroup focuses, CallbackInfo ci) {
        if (recipe.getRollableResults().size() <= 6) return;
        builder.addSlot(RecipeIngredientRole.INPUT, 15, 9).setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().getFirst());
        int index = 0;
        for (var output : recipe.getRollableResults()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 104 + index % 3 * 19, 6 + index / 3 * 19)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(output), -1, -1).addItemStack(output.getStack())
                    .addRichTooltipCallback(CreateRecipeCategory.addStochasticTooltip(output));
            index++;
        }
        ci.cancel();
    }

    // The original arrow moves above the panel at nine outputs; the expanded grid occupies that space.
    @Redirect(method = "draw(Lcom/negodya1/vintageimprovements/content/kinetics/vibration/VibratingRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V", ordinal = 0))
    private void cwi$omitClippedOutputArrow(AllGuiTextures texture, GuiGraphics graphics, int x, int y,
                                          VibratingRecipe recipe, IRecipeSlotsView slots, GuiGraphics originalGraphics, double mouseX, double mouseY) {
        if (recipe.getRollableResults().size() <= 6) texture.render(graphics, x, y);
    }
}
