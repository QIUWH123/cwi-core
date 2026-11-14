package net.quiwh.cwi.mixin.pollution;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.quiwh.cwi.pollution.GasContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SmartFluidTankBehaviour.class,remap=false)
abstract class GasTankBehaviourMixin extends BlockEntityBehaviour {
    protected GasTankBehaviourMixin(SmartBlockEntity owner){super(owner);}
    @Inject(method="initialize",at=@At("TAIL"))
    private void cwi$loaded(CallbackInfo ci){GasContainers.releaseStored(blockEntity);}
    @Override public void onNeighborChanged(BlockPos pos) {
        super.onNeighborChanged(pos);GasContainers.releaseStored(blockEntity);
    }
}
