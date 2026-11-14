package net.quiwh.cwi.mixin.jei;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.quiwh.cwi.compat.jei.ProcessingTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** These categories override JEI's default tooltip method with unscaled raw durations. */
@Mixin(targets = {
        "com.negodya1.vintageimprovements.compat.jei.category.CentrifugationCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.CoilingCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.GrinderPolishingCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.GrinderSandpaperPolishingCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.VibratingCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.LeavesVibratingCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.UnpackingCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.TurningCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.PressurizingCategory",
        "com.negodya1.vintageimprovements.compat.jei.category.VacuumizingCategory"
}, remap = false)
abstract class VintageTimingMixin {
    @Inject(method = "getTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Lnet/minecraft/world/item/crafting/RecipeHolder;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;DD)V",
            at = @At("HEAD"), cancellable = true)
    private void cwi$timing(ITooltipBuilder tooltip, RecipeHolder<? extends Recipe<?>> recipe,
            IRecipeSlotsView slots, double x, double y, CallbackInfo ci) {
        tooltip.addAll(ProcessingTiming.tooltip(this, recipe.value(), x, y));
        ci.cancel();
    }
}
