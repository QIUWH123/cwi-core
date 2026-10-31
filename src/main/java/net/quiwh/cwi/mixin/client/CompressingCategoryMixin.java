package net.quiwh.cwi.mixin.client;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import io.github.hadron13.rubberworks.blocks.compressor.CompressingRecipe;
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
@Mixin(targets = "io.github.hadron13.rubberworks.compat.jei.category.CompressingCategory", remap = false)
abstract class CompressingCategoryMixin {
    @Inject(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lio/github/hadron13/rubberworks/blocks/compressor/CompressingRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At("HEAD"), cancellable = true)
    private void cwi$expandedOutputs(IRecipeLayoutBuilder builder, CompressingRecipe recipe, IFocusGroup focuses, CallbackInfo ci) {
        int count = recipe.getRollableResults().size();
        if (count <= 2) return;
        CreateRecipeCategory.addFluidSlot(builder, 35, 23, recipe.getFluidIngredients().getFirst());
        int top = count <= 3 ? 23 : 4;
        int index = 0;
        for (var output : recipe.getRollableResults()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 114 + index % 3 * 19, top + index / 3 * 19)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(output), -1, -1).addItemStack(output.getStack())
                    .addRichTooltipCallback(CreateRecipeCategory.addStochasticTooltip(output));
            index++;
        }
        ci.cancel();
    }

    @ModifyArgs(method = "draw(Lio/github/hadron13/rubberworks/blocks/compressor/CompressingRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at = @At(value = "INVOKE", target = "Lio/github/hadron13/rubberworks/compat/jei/ModGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V", ordinal = 1))
    private void cwi$moveOutputArrow(Args args, CompressingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (recipe.getRollableResults().size() > 2) args.set(1, 94);
    }
}
