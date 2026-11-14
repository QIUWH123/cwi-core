package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=ProcessingRecipe.class,remap=false)
abstract class ProcessingRecipeMixin {
    @WrapOperation(method="validate",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/processing/recipe/ProcessingRecipe;canSpecifyDuration()Z"))
    private boolean cwi$advancedDuration(ProcessingRecipe<?,?> recipe,Operation<Boolean> original){
        return original.call(recipe)||BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString().equals("createaddition:rolling")&&AdvancedProcessing.execution(recipe);
    }
    @ModifyReturnValue(method="validate",at=@At("RETURN"))
    private List<String> cwi$validate(List<String> original){
        var recipe=(ProcessingRecipe<?,?>)(Object)this;
        var data=((AdvancedParams)recipe.getParams()).cwi$advanced();
        if(data==null)return original;
        var errors=new ArrayList<>(original);
        try{NativeRecipeTypes.validate(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),data);}
        catch(IllegalArgumentException invalid){errors.add(invalid.getMessage());}
        return errors;
    }
    @ModifyReturnValue(method="rollResults(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/List;",at=@At("RETURN"))
    private List<ItemStack> cwi$actionPools(List<ItemStack> original,List<com.simibubi.create.content.processing.recipe.ProcessingOutput> rollable,RandomSource random){
        var recipe=(ProcessingRecipe<?,?>)(Object)this;
        String type=BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();
        if(!Set.of("create:pressing","create:deploying").contains(type))return original;
        var pools=AdvancedProcessing.get(recipe).pools();if(pools.isEmpty())return original;
        var outputs=new ArrayList<>(original);for(var pool:pools)outputs.add(pool.roll(random));return outputs;
    }
}
