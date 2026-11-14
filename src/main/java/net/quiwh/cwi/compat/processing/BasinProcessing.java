package net.quiwh.cwi.compat.processing;

import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.*;
import net.quiwh.cwi.machine.VesselTransaction;

public final class BasinProcessing {
    public static boolean advanced(Recipe<?> recipe){return recipe instanceof ProcessingRecipe<?,?> p&&AdvancedProcessing.execution(p);}
    public static boolean eligible(BasinBlockEntity basin,ProcessingRecipe<?,?> recipe){
        return basin.getLevel() instanceof ServerLevel level&&recipe.getRequiredHeat().testBlazeBurner(((net.quiwh.cwi.mixin.processing.BasinHeatAccess)basin).cwi$heat())&&basin.canContinueProcessing()
            &&VesselTransaction.eligible(AdvancedProcessing.get(recipe).transaction(recipe.getProcessingDuration(),recipe.getRequiredHeat()),
            NativeStorage.items(basin.getInputInventory(),basin.getOutputInventory()),NativeStorage.fluids(basin.getTanks().getFirst(),basin.getTanks().getSecond()),level);
    }
    /** Returns -1 for an ordinary recipe, 0 for paused/working and 1 on completion. */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static int step(SmartBlockEntity entity,double advance){
        if(!(entity.getLevel() instanceof ServerLevel level))return -1;
        var owner=(NativeBasinOwner)entity;var work=NativeWork.of(entity);
        if(work.active()){
            if(!work.restore(level))return 0;
            owner.cwi$recipe(work.recipe());
        }
        if(!advanced(owner.cwi$recipe()))return -1;
        var basin=owner.cwi$basin().orElse(null);
        if(basin==null||!basin.canContinueProcessing())return 0;
        var recipe=(ProcessingRecipe<?,?>)owner.cwi$recipe();
        var items=NativeStorage.items(basin.getInputInventory(),basin.getOutputInventory());
        var fluids=NativeStorage.fluids(basin.getTanks().getFirst(),basin.getTanks().getSecond());
        if(!work.active()){
            var holder=level.getRecipeManager().getRecipes().stream().filter(h->h.value()==recipe).findFirst().orElse(null);
            if(holder==null||!work.start(level,(RecipeHolder)holder,items,fluids))return 0;
        }
        if(advance<=0||!recipe.getRequiredHeat().testBlazeBurner(((net.quiwh.cwi.mixin.processing.BasinHeatAccess)basin).cwi$heat()))return 0;
        if(work.step(level,items,fluids,advance)){basin.notifyChangeOfContents();entity.setChanged();entity.sendData();}
        if(!work.finished())return 0;
        owner.cwi$completed();basin.getTanks().getFirst().sendDataImmediately();
        work.clear();owner.cwi$recipe(null);basin.notifyChangeOfContents();return 1;
    }
    private BasinProcessing(){}
}
