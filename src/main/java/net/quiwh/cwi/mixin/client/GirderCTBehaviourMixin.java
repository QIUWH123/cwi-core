package net.quiwh.cwi.mixin.client;

import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderCTBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.content.weathered.WeatheredGirderBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the native pole texture connected when a weathered pole is its neighbour. */
@Mixin(GirderCTBehaviour.class)
public abstract class GirderCTBehaviourMixin {
    @Inject(method = "connectsTo", at = @At("RETURN"), cancellable = true)
    private void cwi$connectWeatheredPole(BlockState state, BlockState other, BlockAndTintGetter reader,
                                         BlockPos pos, BlockPos otherPos, Direction face,
                                         CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && other.getBlock() instanceof WeatheredGirderBlock
                && !other.getValue(GirderBlock.X) && !other.getValue(GirderBlock.Z)) {
            callback.setReturnValue(true);
        }
    }
}
