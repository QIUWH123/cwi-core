package net.quiwh.cwi.mixin.application;

import com.simibubi.create.compat.jei.category.*;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.quiwh.cwi.compat.jei.ApplicationJei;
import net.quiwh.cwi.interaction.application.ApplicationExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemApplicationCategory.class, remap = false)
abstract class ApplicationCategoryMixin extends CreateRecipeCategory<ItemApplicationRecipe> {
    protected ApplicationCategoryMixin(Info<ItemApplicationRecipe> info) { super(info); }
    @Inject(method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/simibubi/create/content/kinetics/deployer/ItemApplicationRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V",at=@At("HEAD"),cancellable=true)
    private void cwi$layout(IRecipeLayoutBuilder builder,ItemApplicationRecipe recipe,IFocusGroup focus,CallbackInfo ci) {
        if(((ApplicationExtension)recipe).cwi$interaction()!=null) { ApplicationJei.layout(builder,recipe); ci.cancel(); }
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder,RecipeHolder<ItemApplicationRecipe> holder,IFocusGroup focus) {
        super.createRecipeExtras(builder,holder,focus);
        if(((ApplicationExtension)holder.value()).cwi$interaction()!=null) ApplicationJei.extras(builder,holder.value());
    }
}
