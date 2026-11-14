package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.*;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.compat.processing.AdvancedProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=SpoutBlockEntity.class,remap=false)
abstract class SpoutAdvancedMixin {
    @WrapOperation(method="whenItemHeld",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/fluids/spout/FillingBySpout;fillItem(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/item/ItemStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cwi$fill(Level level,int amount,ItemStack stack,FluidStack fluid,Operation<ItemStack> original,
            @Share("products") LocalRef<List<ItemStack>> products){
        var input=new SingleRecipeInput(stack);
        if(SequencedAssemblyRecipe.getRecipe(level,input,AllRecipeTypes.FILLING.getType(),FillingRecipe.class,r->r.value().getRequiredFluid().test(fluid)).isPresent())return original.call(level,amount,stack,fluid);
        for(var holder:level.getRecipeManager().getRecipesFor(AllRecipeTypes.FILLING.<SingleRecipeInput,FillingRecipe>getType(),input,level)){
            var recipe=holder.value();if(!recipe.getRequiredFluid().test(fluid.copyWithAmount(amount)))continue;
            var pools=AdvancedProcessing.get(recipe).pools();if(pools.isEmpty())return original.call(level,amount,stack,fluid);
            var rolled=new ArrayList<>(recipe.rollResults(level.random));for(var pool:pools)rolled.add(pool.roll(level.random));
            fluid.shrink(amount);stack.shrink(1);products.set(rolled.size()<2?List.of():List.copyOf(rolled.subList(1,rolled.size())));
            return rolled.isEmpty()?ItemStack.EMPTY:rolled.getFirst();
        }
        return original.call(level,amount,stack,fluid);
    }
    @WrapOperation(method="whenItemHeld",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;convertToAndLeaveHeld(Ljava/util/List;Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;)Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;"))
    private TransportedResult cwi$allProducts(List<TransportedItemStack> result,TransportedItemStack held,Operation<TransportedResult> original,
            @Share("products") LocalRef<List<ItemStack>> products,@Local(argsOnly=true) TransportedItemStack source){
        if(products.get()!=null)for(var stack:products.get()){var copy=source.copy();copy.stack=stack;result.add(copy);}
        return original.call(result,held);
    }
}
