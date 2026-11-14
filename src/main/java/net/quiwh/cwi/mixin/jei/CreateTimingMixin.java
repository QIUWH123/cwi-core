package net.quiwh.cwi.mixin.jei;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.quiwh.cwi.compat.jei.ProcessingTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CreateRecipeCategory.class, remap = false)
abstract class CreateTimingMixin {
    @ModifyReturnValue(method = "getTooltipStrings(Lnet/minecraft/world/item/crafting/RecipeHolder;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;DD)Ljava/util/List;", at = @At("RETURN"))
    private List<Component> cwi$timing(List<Component> original, RecipeHolder<? extends Recipe<?>> holder,
            IRecipeSlotsView slots, double x, double y) {
        var timing = ProcessingTiming.tooltip(this, holder.value(), x, y);
        if (timing.isEmpty()) return original;
        var result = new ArrayList<>(original); result.addAll(timing); return result;
    }
}
