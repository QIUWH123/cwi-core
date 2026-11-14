package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native matching runs first; stable sorting preserves all equal-priority native ties. */
@Mixin(RecipeManager.class)
abstract class RecipePriorityMixin {
    @ModifyReturnValue(method="getRecipesFor",at=@At("RETURN"))
    private <I extends RecipeInput,R extends Recipe<I>> List<RecipeHolder<R>> cwi$priority(List<RecipeHolder<R>> original){
        if(original.stream().noneMatch(h->AdvancedProcessing.priority(h.value())!=0))return original;
        var sorted=new ArrayList<>(original);sorted.sort(Comparator.<RecipeHolder<R>>comparingInt(h->AdvancedProcessing.priority(h.value())).reversed());return sorted;
    }
    @ModifyReturnValue(method="getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",at=@At("RETURN"))
    private <I extends RecipeInput,R extends Recipe<I>> Optional<RecipeHolder<R>> cwi$first(Optional<RecipeHolder<R>> original,RecipeType<R> type,I input,Level level){
        if(!NativeRecipeTypes.supports(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(type).toString()))return original;
        var recipes=((RecipeManager)(Object)this).getRecipesFor(type,input,level);
        if(recipes.isEmpty()||recipes.stream().noneMatch(h->AdvancedProcessing.priority(h.value())!=0))return original;
        int priority=AdvancedProcessing.priority(recipes.getFirst().value());
        return original.isPresent()&&AdvancedProcessing.priority(original.get().value())==priority?original:Optional.of(recipes.getFirst());
    }
}
