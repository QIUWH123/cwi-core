package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.lucreeper74.createmetallurgy.content.blocks.foundry_basin.FoundryBasinRecipe;
import net.quiwh.cwi.compat.processing.AdvancedProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="fr.lucreeper74.createmetallurgy.compat.jei.category.FoundryBasinCategory",remap=false)
abstract class NativeFoundryJeiMixin {
    @ModifyExpressionValue(method="draw(Lfr/lucreeper74/createmetallurgy/content/blocks/foundry_basin/FoundryBasinRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at=@At(value="INVOKE",target="Ljava/util/List;size()I"))
    private int cwi$poolRows(int count,FoundryBasinRecipe recipe){return count+AdvancedProcessing.get(recipe).pools().size();}
}
