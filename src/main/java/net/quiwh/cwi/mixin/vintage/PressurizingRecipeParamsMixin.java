package net.quiwh.cwi.mixin.vintage;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native params already append secondary stacks to their fluid lists during decoding. */
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.vacuum_chamber.PressurizingRecipeParams")
public abstract class PressurizingRecipeParamsMixin {
    @Inject(method = "secondaryFluidField", at = @At("HEAD"), cancellable = true)
    private void cwi$encodeExistingSecondaryIndex(int index, FluidStack stack,
            CallbackInfoReturnable<Optional<Either<Integer, FluidStack>>> callback) {
        // Encoding the stack again would append it a second time on the next decode.
        if (index >= 0) callback.setReturnValue(Optional.of(Either.left(index)));
    }
}
