package net.quiwh.cwi.mixin.processing;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.world.item.crafting.*;
import net.quiwh.cwi.compat.jei.NativeProcessingJei;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=CreateRecipeCategory.class,remap=false)
abstract class NativeJeiMixin<T extends Recipe<?>> implements IRecipeCategory<RecipeHolder<T>> {
    @Inject(method="setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lnet/minecraft/world/item/crafting/RecipeHolder;Lmezz/jei/api/recipe/IFocusGroup;)V",at=@At("HEAD"),cancellable=true)
    private void cwi$typed(IRecipeLayoutBuilder builder,RecipeHolder<T> holder,IFocusGroup focuses,CallbackInfo ci){
        if(holder.value() instanceof ProcessingRecipe<?,?> recipe&&NativeProcessingJei.handles(recipe)){NativeProcessingJei.layout(builder,recipe,getWidth());ci.cancel();}
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder,RecipeHolder<T> holder,IFocusGroup focuses){
        if(holder.value() instanceof ProcessingRecipe<?,?> recipe&&NativeProcessingJei.handles(recipe))NativeProcessingJei.extras(builder,recipe,getWidth());
    }
}
