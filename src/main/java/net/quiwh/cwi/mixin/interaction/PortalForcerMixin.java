package net.quiwh.cwi.mixin.interaction;

import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deny destination creation before vanilla searches terrain or places any blocks. */
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {
    @Inject(method = "createPortal", at = @At("HEAD"), cancellable = true)
    private void cwi$preventDestinationPortal(BlockPos pos, Direction.Axis axis,
            CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir) {
        cir.setReturnValue(Optional.empty());
    }
}
