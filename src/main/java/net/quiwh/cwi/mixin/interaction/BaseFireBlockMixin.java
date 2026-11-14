package net.quiwh.cwi.mixin.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disabled portals must not make otherwise unsupported fire placement succeed. */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
    @Inject(method = "isPortal", at = @At("HEAD"), cancellable = true)
    private static void cwi$disablePortalIgnitionPlacement(Level level, BlockPos pos, Direction direction,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
