package net.quiwh.cwi.mixin.pollution;

import net.neoforged.neoforge.fluids.*;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.quiwh.cwi.pollution.GasProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=FluidType.class,remap=false)
abstract class GasFluidTypeMixin {
    @Inject(method="canBePlacedInLevel(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/neoforged/neoforge/fluids/FluidStack;)Z",at=@At("HEAD"),cancellable=true)
    private void cwi$stack(BlockAndTintGetter world,BlockPos pos,FluidStack stack,CallbackInfoReturnable<Boolean> cir) {
        if(!stack.isEmpty()&&GasProfiles.fromFluid(stack.getFluid())!=null)cir.setReturnValue(false);
    }
    @Inject(method="canBePlacedInLevel(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)Z",at=@At("HEAD"),cancellable=true)
    private void cwi$state(BlockAndTintGetter world,BlockPos pos,FluidState state,CallbackInfoReturnable<Boolean> cir) {
        if(!state.isEmpty()&&GasProfiles.fromFluid(state.getType())!=null)cir.setReturnValue(false);
    }
}
