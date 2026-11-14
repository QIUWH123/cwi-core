package net.quiwh.cwi.mixin.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.*;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=ItemDrainBlockEntity.class,remap=false)
abstract class DrainAdvancedMixin extends SmartBlockEntity implements NativeDrainProducts {
    @Shadow TransportedItemStack heldItem;
    @Shadow protected int processingTicks;
    @Unique private final ArrayDeque<TransportedItemStack> cwi$products=new ArrayDeque<>();
    protected DrainAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public boolean cwi$pendingProducts(){return !cwi$products.isEmpty();}
    @Inject(method="tick",at=@At("HEAD"))
    private void cwi$next(CallbackInfo ci){
        if(level!=null&&!level.isClientSide&&heldItem==null&&!cwi$products.isEmpty()){
            heldItem=cwi$products.removeFirst();heldItem.beltPosition=heldItem.prevBeltPosition=0.5f;processingTicks=-1;notifyUpdate();
        }
    }
    @Inject(method="tryInsertingFromSide",at=@At("HEAD"),cancellable=true)
    private void cwi$wait(TransportedItemStack stack,Direction side,boolean simulate,CallbackInfoReturnable<ItemStack> cir){if(cwi$pendingProducts())cir.setReturnValue(stack.stack);}
    @WrapOperation(method="continueProcessing",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/fluids/transfer/GenericItemEmptying;emptyItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Z)Lnet/createmod/catnip/data/Pair;"))
    private Pair<FluidStack,ItemStack> cwi$empty(Level world,ItemStack stack,boolean simulate,Operation<Pair<FluidStack,ItemStack>> original){
        if(com.simibubi.create.content.fluids.potion.PotionFluidHandler.isPotionItem(stack))return original.call(world,stack,simulate);
        var holder=AllRecipeTypes.EMPTYING.find(new SingleRecipeInput(stack),world).orElse(null);
        if(holder==null||!(holder.value() instanceof com.simibubi.create.content.fluids.transfer.EmptyingRecipe recipe))return original.call(world,stack,simulate);
        var pools=AdvancedProcessing.get(recipe).pools();if(pools.isEmpty()||simulate)return original.call(world,stack,simulate);
        var rolled=new ArrayList<>(recipe.rollResults(world.random));for(var pool:pools)rolled.add(pool.roll(world.random));
        stack.shrink(1);
        for(int i=1;i<rolled.size();i++){var item=heldItem.copy();item.stack=rolled.get(i);cwi$products.addLast(item);}
        return Pair.of(recipe.getResultingFluid().copy(),rolled.isEmpty()?ItemStack.EMPTY:rolled.getFirst());
    }
    @Inject(method="write",at=@At("TAIL"))
    private void cwi$save(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){
        if(packet)return;var list=new ListTag();for(var stack:cwi$products)list.add(stack.serializeNBT(registries));tag.put("CwiDrainProducts",list);
    }
    @Inject(method="read",at=@At("TAIL"))
    private void cwi$load(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){
        if(packet)return;cwi$products.clear();for(var value:tag.getList("CwiDrainProducts",Tag.TAG_COMPOUND)){
            var stack=TransportedItemStack.read((CompoundTag)value,registries);if(!stack.stack.isEmpty())cwi$products.addLast(stack);
        }
    }
    @Override public void destroy(){
        super.destroy();if(level!=null&&!level.isClientSide){for(var stack:cwi$products)net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack.stack);cwi$products.clear();}
    }
}
