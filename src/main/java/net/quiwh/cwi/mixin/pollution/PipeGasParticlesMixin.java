package net.quiwh.cwi.mixin.pollution;

import com.simibubi.create.content.fluids.PipeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.pollution.GasProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Open-end splashes, pouring and rim drips all share these two entry points. */
@Mixin(value=PipeConnection.class,remap=false)
abstract class PipeGasParticlesMixin {
    @Inject(method={"spawnParticles","spawnSplashOnRim"},at=@At("HEAD"),cancellable=true)
    private void cwi$gasOnly(Level level,BlockPos pos,FluidStack stack,CallbackInfo ci) {
        if(!stack.isEmpty()&&GasProfiles.isGas(stack.getFluid()))ci.cancel();
    }
}
