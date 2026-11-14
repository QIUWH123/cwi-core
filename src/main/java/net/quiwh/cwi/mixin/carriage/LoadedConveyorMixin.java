package net.quiwh.cwi.mixin.carriage;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.quiwh.cwi.content.carriage.LoadedConveyors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Index lifecycle events; discovery never searches or loads world chunks. */
@Mixin(value = SmartBlockEntity.class, remap = false)
abstract class LoadedConveyorMixin {
    @Inject(method = "initialize", at = @At("TAIL"))
    private void cwi$indexConveyor(CallbackInfo ci) {
        if ((Object) this instanceof ChainConveyorBlockEntity conveyor) LoadedConveyors.add(conveyor);
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void cwi$forgetConveyor(CallbackInfo ci) {
        if ((Object) this instanceof ChainConveyorBlockEntity conveyor) LoadedConveyors.remove(conveyor);
    }
}
