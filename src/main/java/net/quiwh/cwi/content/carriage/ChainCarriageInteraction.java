package net.quiwh.cwi.content.carriage;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Uses Create's interaction packet, with the same actual block reach as a placed block. */
public final class ChainCarriageInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos,
            AbstractContraptionEntity contraption) {
        if (!(contraption instanceof ChainCarriageEntity carriage) || !carriage.isAlive()
                || carriage.level() != player.level() || carriage.getContraption() == null || hand == null || !player.mayBuild()
                || player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) return false;
        var block = carriage.getContraption().getBlocks().get(localPos);
        if (block == null || !block.state().is(CarriageContent.BLOCK.get())
                || !carriage.canInteractWithBlock(player, Vec3.atCenterOf(localPos), 0)) return false;
        if (!player.level().isClientSide) carriage.requestDisassembly();
        return true;
    }
}
