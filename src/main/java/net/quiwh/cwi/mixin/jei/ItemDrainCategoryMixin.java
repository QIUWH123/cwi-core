package net.quiwh.cwi.mixin.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.ItemDrainCategory;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.compat.jei.ProcessingChance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemDrainCategory.class, remap = false)
abstract class ItemDrainCategoryMixin {
    @WrapOperation(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/simibubi/create/content/fluids/transfer/EmptyingRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;addItemStack(Lnet/minecraft/world/item/ItemStack;)Lmezz/jei/api/gui/builder/IIngredientAcceptor;"))
    private IIngredientAcceptor<IRecipeSlotBuilder> cwi$chanceTooltip(IRecipeSlotBuilder slot, ItemStack stack,
            Operation<IIngredientAcceptor<IRecipeSlotBuilder>> original, @Local(argsOnly = true) EmptyingRecipe recipe) {
        var result = original.call(slot, stack);
        if (!recipe.getRollableResults().isEmpty()) {
            float chance = recipe.getRollableResults().getFirst().getChance();
            if (chance != 1) slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(ProcessingChance.tooltip(chance)));
        }
        return result;
    }

    @WrapOperation(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/simibubi/create/content/fluids/transfer/EmptyingRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;setBackground(Lmezz/jei/api/gui/drawable/IDrawable;II)Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;", ordinal = 1))
    private IRecipeSlotBuilder cwi$chanceFrame(IRecipeSlotBuilder slot, IDrawable frame, int x, int y,
            Operation<IRecipeSlotBuilder> original, @Local(argsOnly = true) EmptyingRecipe recipe) {
        return original.call(slot, recipe.getRollableResults().isEmpty() ? frame
                : CreateRecipeCategory.getRenderedSlot(recipe.getRollableResults().getFirst()), x, y);
    }
}
