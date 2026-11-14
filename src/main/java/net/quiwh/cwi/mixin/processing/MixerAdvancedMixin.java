package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MechanicalMixerBlockEntity.class,remap=false)
abstract class MixerAdvancedMixin extends BasinOperatingBlockEntity {
    @Shadow public boolean running;
    @Shadow public int runningTicks;
    @Shadow public int processingTicks;
    protected MixerAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Inject(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/processing/basin/BasinOperatingBlockEntity;tick()V",shift=At.Shift.AFTER),cancellable=true)
    private void cwi$work(CallbackInfo ci){
        if(level==null||level.isClientSide)return;
        var work=NativeWork.of(this);
        if(work.active()){
            if(!work.restore((net.minecraft.server.level.ServerLevel)level))return;
            currentRecipe=work.recipe();running=true;runningTicks=20;
        }
        if(!BasinProcessing.advanced(currentRecipe)||!running||runningTicks!=20)return;
        float speed=Math.abs(getSpeed());
        int base=((com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?>)currentRecipe).getProcessingDuration();
        if(base==0)base=100;
        int duration=Math.max(net.minecraft.util.Mth.log2((int)(512/Math.max(1,speed)))*(int)Math.ceil(base/100f*15)+1,1);
        int result=BasinProcessing.step(this,speed>0&&isSpeedRequirementFulfilled()?1d/duration:0);
        if(result==1){runningTicks=21;processingTicks=-1;basinChecker.scheduleUpdate();sendData();}
        ci.cancel();
    }
}
