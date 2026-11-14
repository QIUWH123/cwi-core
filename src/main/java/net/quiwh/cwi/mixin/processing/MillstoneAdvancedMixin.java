package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.quiwh.cwi.compat.processing.StoredProcessing;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MillstoneBlockEntity.class,remap=false)
abstract class MillstoneAdvancedMixin extends KineticBlockEntity {
    @Shadow public ItemStackHandler inputInv;
    @Shadow public ItemStackHandler outputInv;
    @Shadow public int timer;
    @Shadow public abstract int getProcessingSpeed();
    protected MillstoneAdvancedMixin(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Inject(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;tick()V",shift=At.Shift.AFTER),cancellable=true)
    private void cwi$periodic(CallbackInfo ci){if(StoredProcessing.tick(this,inputInv,outputInv,AllRecipeTypes.MILLING.getType(),getProcessingSpeed(),value->timer=value,()->award(com.simibubi.create.foundation.advancement.AllAdvancements.MILLSTONE))){ci.cancel();}}
}
