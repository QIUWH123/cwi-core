package net.quiwh.cwi.mixin.processing;
import com.negodya1.vintageimprovements.VintageRecipes;
import com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugationRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.item.SmartInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeBlockEntity",remap=false)
abstract class CentrifugeAdvancedMixin extends KineticBlockEntity {
    @Shadow public SmartInventory inputInv;
    @Shadow public SmartInventory outputInv;
    @Shadow public SmartFluidTankBehaviour inputTank;
    @Shadow public SmartFluidTankBehaviour outputTank;
    @Shadow public int timer;
    @Shadow com.negodya1.vintageimprovements.foundation.advancement.VintageAdvancementBehaviour advancementBehaviour;
    @Shadow public abstract int getBasins();
    @Shadow public abstract int getProcessingSpeed();
    protected CentrifugeAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Inject(method="tick",at=@At(value="FIELD",target="Lcom/negodya1/vintageimprovements/content/kinetics/centrifuge/CentrifugeBlockEntity;timer:I",ordinal=0),cancellable=true)
    private void cwi$work(CallbackInfo ci){
        if(!(level instanceof ServerLevel server))return;
        var work=NativeWork.of(this);var items=NativeStorage.items(inputInv,outputInv);var fluids=NativeStorage.fluids(inputTank,outputTank);
        if(!work.active()){
            for(int i=0;i<inputInv.getSlots();i++)if(SequencedAssemblyRecipe.getRecipe(level,inputInv.getStackInSlot(i),VintageRecipes.CENTRIFUGATION.getType(),CentrifugationRecipe.class).isPresent())return;
            var found=NativeWork.candidates(server,VintageRecipes.CENTRIFUGATION.getType(),p->true);
            if(found.stream().noneMatch(h->AdvancedProcessing.execution(h.value())||AdvancedProcessing.priority(h.value())!=0))return;
            for(var candidate:found)if(work.start(server,candidate,items,fluids))break;
            if(!work.active()){ci.cancel();return;}
        }
        if(work.restore(server)&&getBasins()>=4&&getSpeed()!=0&&Math.abs(getSpeed())>=((CentrifugationRecipe)work.recipe()).getMinimalRPM()){
            boolean changed=work.step(server,items,fluids,getProcessingSpeed()/work.duration());
            timer=(int)Math.ceil(work.duration()*(1-work.progress()));
            if(work.finished()){advancementBehaviour.awardVintageAdvancement(com.negodya1.vintageimprovements.foundation.advancement.VintageAdvancements.USE_CENTRIFUGE);work.clear();timer=0;}
            if(changed){setChanged();sendData();}
        }
        ci.cancel();
    }
}
