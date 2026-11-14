package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.quiwh.cwi.compat.processing.AdvancedProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=BeltDeployerCallbacks.class,remap=false)
abstract class DeployerAdvancedMixin {
    @WrapOperation(method="activate",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/deployer/ItemApplicationRecipe;shouldKeepHeldItem()Z"))
    private static boolean cwi$retain(ItemApplicationRecipe recipe,Operation<Boolean> original){
        if(recipe instanceof ProcessingRecipe<?,?> processing){
            var data=AdvancedProcessing.get(processing);
            if(data.ingredients().size()==2){
                var settings=data.ingredients().get(1).map(v->v.settings(),v->v.settings());
                if(settings.catalyst())return true;
                if(settings.damage()>0)return false;
            }
        }
        return original.call(recipe);
    }
    @WrapOperation(method="activate",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void cwi$damage(ItemStack held,int amount,LivingEntity user,EquipmentSlot slot,Operation<Void> original,
            @Local(argsOnly=true) Recipe<?> recipe){
        if(recipe instanceof ProcessingRecipe<?,?> processing){
            var inputs=AdvancedProcessing.get(processing).ingredients();
            if(inputs.size()==2)amount=Math.max(amount,inputs.get(1).map(v->v.settings().damage(),v->v.settings().damage()));
        }
        original.call(held,amount,user,slot);
    }
}
