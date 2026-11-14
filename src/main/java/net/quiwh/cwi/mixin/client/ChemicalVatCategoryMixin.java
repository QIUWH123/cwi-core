package net.quiwh.cwi.mixin.client;

import com.drmangotea.tfmg.recipes.VatMachineRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.item.ItemHelper;
import java.util.ArrayList;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "com.drmangotea.tfmg.integration.jei.category.ChemicalVatCategory", remap = false)
abstract class ChemicalVatCategoryMixin {
    private static boolean cwi$expanded(VatMachineRecipe recipe) {
        if(net.quiwh.cwi.compat.jei.NativeProcessingJei.handles(recipe))
            return net.quiwh.cwi.compat.jei.NativeProcessingJei.itemInputs(recipe)>4||net.quiwh.cwi.compat.jei.NativeProcessingJei.itemOutputs(recipe)>4;
        return ItemHelper.condenseIngredients(recipe.getIngredients()).size() > 4 || recipe.getRollableResults().size() > 4;
    }

    @Inject(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/drmangotea/tfmg/recipes/VatMachineRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At("HEAD"), cancellable = true)
    private void cwi$expandedItems(IRecipeLayoutBuilder builder, VatMachineRecipe recipe, IFocusGroup focuses, CallbackInfo ci) {
        if (!cwi$expanded(recipe)) return;
        int index = 0;
        for (var ingredient : ItemHelper.condenseIngredients(recipe.getIngredients())) {
            var stacks = new ArrayList<ItemStack>();
            for (var stack : ingredient.getFirst().getItems()) stacks.add(stack.copyWithCount(ingredient.getSecond().intValue()));
            builder.addSlot(RecipeIngredientRole.INPUT, 19 + index % 4 * 21, 43 + index / 4 * 21)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1).addItemStacks(stacks);
            index++;
        }
        for (int i = 0; i < recipe.getFluidIngredients().size(); i++)
            CreateRecipeCategory.addFluidSlot(builder, 19 + i * 21, 85, recipe.getFluidIngredients().get(i));
        for (int i = 0; i < recipe.getRollableResults().size(); i++) {
            var output = recipe.getRollableResults().get(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 110 + i % 2 * 21, 43 + i / 2 * 21)
                    .setBackground(CreateRecipeCategory.getRenderedSlot(output), -1, -1).addItemStack(output.getStack())
                    .addRichTooltipCallback(CreateRecipeCategory.addStochasticTooltip(output));
        }
        for (int i = 0; i < recipe.getFluidResults().size(); i++)
            CreateRecipeCategory.addFluidSlot(builder, 153, 43 + i * 21, recipe.getFluidResults().get(i));
        ci.cancel();
    }

    // Slot builders provide backgrounds for the expanded grid; omit the native one-row decorations.
    @Redirect(method = "draw(Lcom/drmangotea/tfmg/recipes/VatMachineRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at = @At(value = "INVOKE", target = "Lcom/drmangotea/tfmg/registry/TFMGGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void cwi$slotDecorations(com.drmangotea.tfmg.registry.TFMGGuiTextures texture, GuiGraphics graphics, int x, int y,
            VatMachineRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, GuiGraphics originalGraphics, double mouseX, double mouseY) {
        if ((!cwi$expanded(recipe)&&!net.quiwh.cwi.compat.jei.NativeProcessingJei.handles(recipe)) || texture != com.drmangotea.tfmg.registry.TFMGGuiTextures.SLOT) texture.render(graphics, x, y);
    }

    @ModifyArgs(method = "draw(Lcom/drmangotea/tfmg/recipes/VatMachineRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private void cwi$minimumSizePosition(Args args, VatMachineRecipe recipe,
            mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (cwi$expanded(recipe)) { args.set(2, 10); args.set(3, 24); }
    }
}
