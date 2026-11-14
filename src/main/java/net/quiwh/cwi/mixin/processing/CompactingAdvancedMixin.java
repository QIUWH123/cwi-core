package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.content.kinetics.press.*;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PressingBehaviour.class,remap=false)
abstract class CompactingAdvancedMixin {
    @Shadow public PressingBehaviour.PressingBehaviourSpecifics specifics;
    @Shadow public boolean running;
    @Shadow public int runningTicks;
    @Shadow public abstract boolean onBasin();
    @Shadow public abstract int getRunningTickSpeed();
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void cwi$step(CallbackInfo ci){
        if(!running||!onBasin()||!(specifics instanceof SmartBlockEntity entity)||!(entity.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        var owner=(NativeBasinOwner)entity;var work=NativeWork.of(entity);
        if(work.active()&&work.restore(level))owner.cwi$recipe(work.recipe());
        if(!BasinProcessing.advanced(owner.cwi$recipe())||runningTicks>120)return;
        double advance=Math.max(0,Math.min(120,runningTicks+getRunningTickSpeed())-runningTicks)/120d;
        double before=work.progress();int result=BasinProcessing.step(entity,advance);
        if(result==0&&(advance<=0||work.progress()==before))ci.cancel();
    }
}
