package net.quiwh.cwi.mixin.interaction;

import com.simibubi.create.api.behaviour.spouting.BlockSpoutingBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.quiwh.cwi.interaction.WorldSpouting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve existing Create/addon behaviours when no configured rule accepts the fluid. */
@Mixin(value = BlockSpoutingBehaviour.class, remap = false)
public interface SpoutingBehaviourMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void cwi$recipes(Level level, BlockPos pos, CallbackInfoReturnable<BlockSpoutingBehaviour> cir) {
        if (!WorldSpouting.hasRules(level, pos)) return;
        BlockSpoutingBehaviour original = cir.getReturnValue();
        cir.setReturnValue((world, target, spout, fluid, simulate) -> {
            int result = WorldSpouting.fill(world, target, fluid, simulate);
            return result >= 0 ? result : original == null ? 0 : original.fillBlock(world, target, spout, fluid, simulate);
        });
    }
}
