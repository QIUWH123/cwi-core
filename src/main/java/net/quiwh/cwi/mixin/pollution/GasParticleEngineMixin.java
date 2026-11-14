package net.quiwh.cwi.mixin.pollution;

import net.minecraft.client.particle.ParticleEngine;
import net.quiwh.cwi.pollution.client.GasParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
abstract class GasParticleEngineMixin {
    @Inject(method="clearParticles",at=@At("HEAD"))
    private void cwi$clear(CallbackInfo ci){GasParticle.reset();}
}
