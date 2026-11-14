package net.quiwh.cwi.mixin.application;

import com.simibubi.create.AllRecipeTypes;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.quiwh.cwi.interaction.application.ApplicationExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = AllRecipeTypes.class, remap = false)
abstract class ApplicationAutomationMixin {
    @Inject(method = "lambda$static$4", at = @At("HEAD"), cancellable = true)
    private static void cwi$manualOnly(RecipeHolder<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (recipe.value() instanceof ApplicationExtension extension && extension.cwi$interaction() != null) cir.setReturnValue(false);
    }
}
