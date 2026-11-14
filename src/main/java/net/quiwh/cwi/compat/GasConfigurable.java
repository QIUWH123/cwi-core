package net.quiwh.cwi.compat;

import java.util.function.Consumer;
import dev.latvian.mods.kubejs.fluid.FluidBuilder;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.quiwh.cwi.pollution.GasProfile;

/** Attached to the ordinary KubeJS fluid builder, including inherited builders. */
public interface GasConfigurable {
    @HideFromJS GasProfile.Builder cwi$gasBuilder();
    default FluidBuilder gas(Consumer<GasProfile.Builder> configure) {
        configure.accept(cwi$gasBuilder());
        return ((FluidBuilder)this).noBlock();
    }
}
