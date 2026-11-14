package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.compat.jei.category.BasinCategory;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.quiwh.cwi.compat.processing.AdvancedProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native output arrows account for the independent pool slots too. */
@Mixin(value=BasinCategory.class,remap=false)
abstract class NativeBasinJeiMixin {
    @ModifyExpressionValue(method="draw(Lcom/simibubi/create/content/processing/basin/BasinRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at=@At(value="INVOKE",target="Ljava/util/List;size()I"))
    private int cwi$poolRows(int count,BasinRecipe recipe){return count+AdvancedProcessing.get(recipe).pools().size();}
}
