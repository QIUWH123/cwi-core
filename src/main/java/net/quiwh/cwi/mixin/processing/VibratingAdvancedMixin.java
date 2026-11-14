package net.quiwh.cwi.mixin.processing;
import com.negodya1.vintageimprovements.VintageRecipes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.foundation.item.SmartInventory;
import net.quiwh.cwi.compat.processing.StoredProcessing;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.negodya1.vintageimprovements.content.kinetics.vibration.VibratingTableBlockEntity",remap=false)
abstract class VibratingAdvancedMixin extends KineticBlockEntity {
    @Shadow public SmartInventory inputInv;
    @Shadow public SmartInventory outputInv;
    @Shadow public int timer;
    @Shadow com.negodya1.vintageimprovements.foundation.advancement.VintageAdvancementBehaviour advancementBehaviour;
    @Shadow public abstract int getProcessingSpeed();
    protected VibratingAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Inject(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;tick()V",shift=At.Shift.AFTER),cancellable=true)
    private void cwi$periodic(CallbackInfo ci){if(StoredProcessing.tick(this,inputInv,outputInv,VintageRecipes.VIBRATING.getType(),getProcessingSpeed(),value->timer=value,()->advancementBehaviour.awardVintageAdvancement(com.negodya1.vintageimprovements.foundation.advancement.VintageAdvancements.USE_VIBRATION_TABLE))){ci.cancel();}}
}
