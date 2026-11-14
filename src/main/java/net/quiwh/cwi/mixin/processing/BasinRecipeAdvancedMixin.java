package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BasinRecipe.class,remap=false)
abstract class BasinRecipeAdvancedMixin {
    @Inject(method="apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",at=@At("HEAD"),cancellable=true)
    private static void cwi$match(BasinBlockEntity basin,Recipe<?> recipe,boolean test,CallbackInfoReturnable<Boolean> cir){
        if(BasinProcessing.advanced(recipe))cir.setReturnValue(test&&BasinProcessing.eligible(basin,(ProcessingRecipe<?,?>)recipe));
    }
}
