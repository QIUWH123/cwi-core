package net.quiwh.cwi.mixin.processing;

import com.jesz.createdieselgenerators.CDGRecipes;
import com.jesz.createdieselgenerators.content.distillation.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="com.jesz.createdieselgenerators.content.distillation.DistillationTankBlockEntity",remap=false)
abstract class DieselDistillationAdvancedMixin {
    @Shadow int processingTime;
    @Shadow DistillationRecipe currentRecipe;
    @Shadow BlazeBurnerBlock.HeatLevel highestHeatLevel;
    @Shadow int numberOfHighestHeat;
    @Shadow public float progress;
    @Shadow boolean tanksFull;
    @Shadow public abstract boolean isBottom();
    @Shadow private boolean isSameMultiBlock(DistillationTankBlockEntity other){throw new AssertionError();}

    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lcom/jesz/createdieselgenerators/content/distillation/DistillationTankBlockEntity;isController()Z",ordinal=0))
    private boolean cwi$nativePhase(DistillationTankBlockEntity self,Operation<Boolean> original){
        boolean controller=original.call(self);
        return controller&&!cwi$process(self);
    }
    @Unique private List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> cwi$stages(DistillationTankBlockEntity self,int count){
        var stages=new ArrayList<net.neoforged.neoforge.fluids.capability.templates.FluidTank>();
        for(int i=0;i<count;i++){
            if(!(self.getLevel().getBlockEntity(self.getBlockPos().above(i+1)) instanceof DistillationTankBlockEntity stage)||!isSameMultiBlock(stage))return null;
            stages.add(stage.tankInventory);
        }
        return stages;
    }
    @Unique private boolean cwi$process(DistillationTankBlockEntity self){
        if(!(self.getLevel() instanceof ServerLevel server)||!isBottom())return false;
        var work=NativeWork.of(self);
        if(work.active()&&!work.restore(server)){currentRecipe=null;processingTime=-1;return true;}
        if(work.active())currentRecipe=(DistillationRecipe)work.recipe();
        var choices=NativeWork.candidates(server,CDGRecipes.DISTILLATION.getType(),p->p.getRequiredHeat().testBlazeBurner(highestHeatLevel)
                &&!p.getFluidIngredients().isEmpty()&&p.getFluidIngredients().getFirst().ingredient().test(self.tankInventory.getFluid()));
        // Preserve native heat ordering when priorities tie.
        choices=new ArrayList<>(choices);choices.sort(Comparator.<net.minecraft.world.item.crafting.RecipeHolder<? extends com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?>>>comparingInt(h->AdvancedProcessing.priority(h.value())).reversed()
                .thenComparing(Comparator.comparingInt((net.minecraft.world.item.crafting.RecipeHolder<? extends com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?>> h)->h.value().getRequiredHeat().ordinal()).reversed()));
        if(!work.active()&&choices.stream().noneMatch(h->AdvancedProcessing.execution(h.value())||AdvancedProcessing.priority(h.value())!=0))return false;
        if(!work.active()){
            currentRecipe=null;
            for(var holder:choices){
                var stages=cwi$stages(self,holder.value().getFluidResults().size());if(stages==null)continue;
                var fluids=NativeStorage.routed(self.tankInventory,stages,i->false);
                for(int batch=Math.min(64,numberOfHighestHeat);batch>0;batch--)
                    if(work.start(server,holder,NativeStorage.EMPTY_ITEMS,fluids,batch,Math.max(1,holder.value().getProcessingDuration())))break;
                if(work.active())break;
            }
            if(!work.active())return true;
            currentRecipe=(DistillationRecipe)work.recipe();
        }
        var stages=cwi$stages(self,currentRecipe.getFluidResults().size());if(stages==null)return true;
        var fluids=NativeStorage.routed(self.tankInventory,stages,i->false);
        if(!currentRecipe.getRequiredHeat().testBlazeBurner(highestHeatLevel)||numberOfHighestHeat<=0)return true;
        boolean moved=work.step(server,NativeStorage.EMPTY_ITEMS,fluids,1d/work.duration());tanksFull=!moved;
        processingTime=(int)Math.ceil(work.duration()*(1-work.progress()));progress=(float)(1-work.progress());
        if(moved){self.setChanged();self.sendData();}
        if(work.finished()){
            work.clear();currentRecipe=null;processingTime=-1;progress=0;
            server.playSound(null,self.getBlockPos(),net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW,net.minecraft.sounds.SoundSource.BLOCKS,0.05f,0.5f);
            server.playSound(null,self.getBlockPos(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,0.05f,0.5f);
        }
        return true;
    }
}
