package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="fr.lucreeper74.createmetallurgy.content.blocks.foundry_lid.FoundryLidBlockEntity",remap=false)
abstract class LidAdvancedMixin extends BasinOperatingBlockEntity {
    @Shadow public boolean running;
    @Shadow public int processingTime;
    protected LidAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Inject(method="tick",at=@At(value="INVOKE",target="Lfr/lucreeper74/createmetallurgy/content/blocks/foundry_basin/FoundryBasinOperatingBE;tick()V",shift=At.Shift.AFTER),cancellable=true)
    private void cwi$work(CallbackInfo ci){
        if(level==null||level.isClientSide)return;
        var work=NativeWork.of(this);if(work.active()&&work.restore((net.minecraft.server.level.ServerLevel)level)){currentRecipe=work.recipe();running=true;}
        if(!BasinProcessing.advanced(currentRecipe)||!running)return;
        int duration=Math.max(1,((com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?>)currentRecipe).getProcessingDuration());
        int result=BasinProcessing.step(this,1d/duration);
        processingTime=(int)Math.ceil(duration*(1-work.progress()));
        if(result==1){processingTime=-1;running=false;basinChecker.scheduleUpdate();sendData();}
        ci.cancel();
    }
}
