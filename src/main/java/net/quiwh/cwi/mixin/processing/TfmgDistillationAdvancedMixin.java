package net.quiwh.cwi.mixin.processing;

import com.drmangotea.tfmg.content.decoration.tanks.steel.SteelTankBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output.DistillationOutputBlockEntity;
import com.drmangotea.tfmg.recipes.DistillationRecipe;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity",remap=false)
abstract class TfmgDistillationAdvancedMixin extends SmartBlockEntity {
    @Shadow @Final public SmartFluidTank tank;
    @Shadow public DistillationRecipe currentRecipe;
    @Shadow public List<BlockPos> outputs;
    @Shadow protected int recipeDuration;
    @Shadow int timer;
    @Shadow public abstract DistillationOutputBlockEntity getOutput(BlockPos pos);
    protected TfmgDistillationAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}

    @Inject(method="findRecipe",at=@At("HEAD"),cancellable=true)
    private void cwi$selection(CallbackInfoReturnable<DistillationRecipe> cir){
        if(!(level instanceof ServerLevel server))return;
        var work=NativeWork.of(this);
        if(work.active()&&work.restore(server)){cir.setReturnValue((DistillationRecipe)work.recipe());return;}
        var choices=NativeWork.candidates(server,TFMGRecipeTypes.DISTILLATION.getType(),p->p.getFluidResults().size()==outputs.size()
                &&!p.getFluidIngredients().isEmpty()&&p.getFluidIngredients().getFirst().ingredient().test(tank.getFluid()));
        if(choices.stream().noneMatch(h->AdvancedProcessing.execution(h.value())||AdvancedProcessing.priority(h.value())!=0))return;
        for(var choice:choices){
            var data=AdvancedProcessing.get(choice.value());
            if(data.ingredients().stream().allMatch(e->e.map(v->v.ingredient().test(tank.getFluid()),v->false))){
                recipeDuration=Math.max(1,choice.value().getProcessingDuration());cir.setReturnValue((DistillationRecipe)choice.value());return;
            }
        }
        cir.setReturnValue(null);
    }

    @Inject(method="manageRecipe",at=@At("HEAD"),cancellable=true)
    private void cwi$process(SteelTankBlockEntity steel,CallbackInfo ci){
        if(!(level instanceof ServerLevel server))return;
        var work=NativeWork.of(this);
        if(work.active()){
            if(!work.restore(server)){currentRecipe=null;timer=0;ci.cancel();return;}
            currentRecipe=(DistillationRecipe)work.recipe();
        }
        if(currentRecipe==null||!AdvancedProcessing.execution(currentRecipe))return;
        ci.cancel();
        if(steel.activeHeat<=0||outputs.size()!=currentRecipe.getFluidResults().size()
                ||steel.getHeight()<outputs.size()*2||steel.getWidth()<2&&outputs.size()>3)return;
        var stages=new ArrayList<DistillationOutputBlockEntity>();
        for(var pos:outputs){var stage=getOutput(pos);if(stage==null)return;stages.add(stage);}
        var fluids=NativeStorage.routed(tank,stages.stream().map(s->s.tank).toList(),i->stages.get(i).mode.get()==DistillationOutputBlockEntity.DistillationOutputMode.VOID_WHEN_FULL);
        if(!work.active()){
            var holder=NativeWork.candidates(server,TFMGRecipeTypes.DISTILLATION.getType(),p->p==currentRecipe).stream().findFirst().orElse(null);
            if(holder==null||!work.start(server,holder,NativeStorage.EMPTY_ITEMS,fluids))return;
        }
        if(work.step(server,NativeStorage.EMPTY_ITEMS,fluids,steel.activeHeat/work.duration())){
            timer=(int)Math.floor(work.progress()*work.duration());recipeDuration=(int)work.duration();setChanged();sendData();
        }
        if(work.finished()){work.clear();currentRecipe=null;timer=0;recipeDuration=-1;}
    }
    // Upstream's write override does not call SmartBlockEntity.write.
    @Inject(method="write",at=@At("TAIL"))
    private void cwi$save(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){
        var work=NativeWork.of(this);if(work.active())tag.put("CwiProcessing",work.save());
    }
}
