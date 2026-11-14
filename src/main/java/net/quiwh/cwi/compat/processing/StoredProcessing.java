package net.quiwh.cwi.compat.processing;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.server.level.ServerLevel;
import java.util.function.IntConsumer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** Single-input native mills retain their input slot and distinct product inventory. */
public final class StoredProcessing {
    @SuppressWarnings({"rawtypes","unchecked"})
    public static boolean tick(KineticBlockEntity entity,IItemHandlerModifiable input,IItemHandlerModifiable output,RecipeType<?> type,double speed,IntConsumer timer,Runnable completion){
        if(!(entity.getLevel() instanceof ServerLevel level))return false;
        var work=NativeWork.of(entity);var items=NativeStorage.items(input,output);
        boolean changed=false;
        if(!work.active()){
            if(input.getStackInSlot(0).has(AllDataComponents.SEQUENCED_ASSEMBLY))return false;
            var wrapper=new RecipeWrapper(input);
            var found=NativeWork.candidates(level,type,p->((ProcessingRecipe)p).matches(wrapper,level));
            if(found.stream().noneMatch(h->AdvancedProcessing.execution(h.value())||AdvancedProcessing.priority(h.value())!=0))return false;
            boolean selected=false;
            for(var candidate:found)if(work.start(level,candidate,items,NativeStorage.EMPTY_FLUIDS)){selected=true;break;}
            if(!selected){timer.accept(0);return true;}
            changed=true;
        }
        if(!work.restore(level)){timer.accept(0);return false;}
        if(entity.getSpeed()!=0&&speed>0)changed|=work.step(level,items,NativeStorage.EMPTY_FLUIDS,speed/work.duration());
        // Set the native timer before sendData: native clients use it for processing particles.
        timer.accept((int)Math.ceil(work.duration()*(1-work.progress())));
        if(work.finished()){completion.run();work.clear();}
        if(changed){entity.setChanged();entity.sendData();}
        return true;
    }
    private StoredProcessing(){}
}
