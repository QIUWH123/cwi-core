package net.quiwh.cwi.mixin.pollution.kubejs;

import dev.latvian.mods.kubejs.fluid.FluidBuilder;
import dev.latvian.mods.kubejs.registry.AdditionalObjectRegistry;
import net.quiwh.cwi.compat.GasConfigurable;
import net.quiwh.cwi.pollution.GasProfile;
import net.quiwh.cwi.pollution.GasProfiles;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FluidBuilder.class,remap=false)
public abstract class FluidGasMixin implements GasConfigurable {
    @Unique private GasProfile.Builder cwi$profile;
    @Override public GasProfile.Builder cwi$gasBuilder() {
        if(cwi$profile==null)cwi$profile=GasProfiles.configure(((FluidBuilder)(Object)this).id.toString());
        return cwi$profile;
    }
    @Inject(method="createAdditionalObjects",at=@At("HEAD"))
    private void cwi$prepare(AdditionalObjectRegistry registry,CallbackInfo ci) {
        if(cwi$profile==null)return;
        var builder=(FluidBuilder)(Object)this;
        builder.noBlock();
        if(builder.fluidType.tint!=null)cwi$profile.color(builder.fluidType.tint.kjs$getRGB());
    }
}
