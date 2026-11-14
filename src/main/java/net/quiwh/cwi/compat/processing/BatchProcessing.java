package net.quiwh.cwi.compat.processing;

import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Bulk processors retain their native product-transport phase after transactional processing. */
public final class BatchProcessing {
    public static boolean tick(SmartBlockEntity entity,ProcessingInventory inventory,List<? extends RecipeHolder<?>> recipes,double rate,boolean saw,java.util.function.Consumer<ItemStack> completion){
        if(!(entity.getLevel() instanceof ServerLevel level))return false;
        var work=NativeWork.of(entity);
        if(work.active()&&work.finished()){
            for(int slot=1;slot<inventory.getSlots();slot++)if(!inventory.getStackInSlot(slot).isEmpty())return false;
            clearTransport(entity,inventory);return true;
        }
        if(inventory.appliedRecipe&&!work.active())return false;
        var input=NativeStorage.range(inventory,0,1);
        var output=NativeStorage.range(inventory,1,inventory.getSlots());
        var items=NativeStorage.items(input,output);
        if(!work.active()){
            if(recipes.isEmpty()||recipes.stream().noneMatch(h->h.value() instanceof ProcessingRecipe<?,?> p&&(AdvancedProcessing.execution(p)||AdvancedProcessing.priority(p)!=0)))return false;
            for(var candidate:recipes){
                if(!(candidate.value() instanceof ProcessingRecipe<?,?> recipe))continue;
                if(level.getRecipeManager().byKey(candidate.id()).map(RecipeHolder::value).orElse(null) instanceof SequencedAssemblyRecipe)return false;
                int batch=copies(recipe,input.getStackInSlot(0));
                double duration=Math.max(1,recipe.getProcessingDuration())*(saw?Math.max(1,input.getStackInSlot(0).getCount()/5):1);
                @SuppressWarnings({"unchecked","rawtypes"}) var holder=(RecipeHolder<? extends ProcessingRecipe<?,?>>)(RecipeHolder)candidate;
                if(work.start(level,holder,items,NativeStorage.EMPTY_FLUIDS,batch,duration))break;
            }
            if(!work.active())return true;
        }
        if(rate<=0||!work.restore(level))return true;
        boolean changed=work.step(level,items,NativeStorage.EMPTY_FLUIDS,rate/work.duration());
        inventory.recipeDuration=(float)work.duration();inventory.remainingTime=(float)Math.max(1,work.duration()*(1-work.progress()));
        if(work.finished()){inventory.appliedRecipe=true;inventory.remainingTime=20;if(saw)inventory.recipeDuration=20;completion.accept(work.sourceItem(level));}
        if(changed){entity.setChanged();entity.sendData();}
        return true;
    }
    public static int copies(ProcessingRecipe<?,?> recipe,ItemStack input){
        int amount=AdvancedProcessing.get(recipe).ingredients().stream().mapToInt(e->e.map(v->0,v->v.perTick()?0:v.count())).sum();
        return amount==0?1:Math.clamp(input.getCount()/amount,1,64);
    }
    public static boolean eligible(ServerLevel level,ProcessingInventory inventory,ProcessingRecipe<?,?> recipe){
        var items=NativeStorage.items(NativeStorage.range(inventory,0,1),NativeStorage.range(inventory,1,inventory.getSlots()));
        return net.quiwh.cwi.machine.VesselTransaction.eligible(AdvancedProcessing.get(recipe).transaction(NativeWork.defaultDuration(recipe),recipe.getRequiredHeat()),
                items,NativeStorage.EMPTY_FLUIDS,level,copies(recipe,inventory.getStackInSlot(0)));
    }
    public static ItemStack visible(SmartBlockEntity entity,ProcessingInventory inventory,int slot){
        return slot==0&&NativeWork.of(entity).finished()?ItemStack.EMPTY:inventory.getStackInSlot(slot);
    }
    public static void clearTransport(SmartBlockEntity entity,ProcessingInventory inventory){
        var work=NativeWork.of(entity);if(!work.finished()){inventory.clear();return;}
        var input=inventory.getStackInSlot(0).copy();inventory.clear();work.clear();inventory.setStackInSlot(0,input);
        if(!input.isEmpty())inventory.callback.accept(input);
    }
    private BatchProcessing(){}
}
