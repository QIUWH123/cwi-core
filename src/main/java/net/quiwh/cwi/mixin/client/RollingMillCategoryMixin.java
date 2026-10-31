package net.quiwh.cwi.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "com.mrh0.createaddition.compat.jei.RollingMillCategory", remap = false)
abstract class RollingMillCategoryMixin {
    @ModifyArgs(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/mrh0/createaddition/recipe/rolling/RollingRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;addSlot(Lmezz/jei/api/recipe/RecipeIngredientRole;II)Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;"))
    private void cwi$alignMillstoneOutput(Args args) {
        args.set(1, 139);
        args.set(2, 27);
    }
}
