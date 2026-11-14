package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.quiwh.cwi.compat.processing.AdvancedProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=ItemApplicationRecipe.class,remap=false)
abstract class DeployerRecipeAdvancedMixin {
    @ModifyReturnValue(method="matches(Lnet/neoforged/neoforge/items/wrapper/RecipeWrapper;Lnet/minecraft/world/level/Level;)Z",at=@At("RETURN"))
    private boolean cwi$tool(boolean matches,RecipeWrapper input,Level level){
        if(!matches)return false;
        var ingredients=AdvancedProcessing.get((ProcessingRecipe<?,?>)(Object)this).ingredients();
        if(ingredients.size()!=2||ingredients.get(1).map(v->0,v->v.settings().damage())==0)return true;
        var held=input.getItem(1);
        return held.getCount()==1&&held.isDamageableItem()&&!held.has(DataComponents.UNBREAKABLE);
    }
}
