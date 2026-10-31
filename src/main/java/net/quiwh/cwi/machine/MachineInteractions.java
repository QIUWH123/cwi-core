package net.quiwh.cwi.machine;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Manual access uses exactly the same sided handlers as automation. The sealed vessel opts out. */
final class MachineInteractions {
    static ItemInteractionResult use(ItemStack stack, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.mayBuild()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.isEmpty()) {
            var items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, hit.getDirection());
            boolean retrieved = false;
            if (items != null) for (int slot = 0; slot < items.getSlots(); slot++) {
                var extracted = items.extractItem(slot, Integer.MAX_VALUE, level.isClientSide);
                if (extracted.isEmpty()) continue;
                if (!level.isClientSide) player.getInventory().placeItemBackInInventory(extracted);
                retrieved = true;
            }
            if (retrieved) {
                if (!level.isClientSide) level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, 1);
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, hit.getDirection());
        if (fluids == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (GenericItemEmptying.canItemBeEmptied(level, stack)) {
            var result = GenericItemEmptying.emptyItem(level, stack, true);
            var fluid = result.getFirst();
            if (!fluid.isEmpty() && fluids.fill(fluid, FluidAction.SIMULATE) == fluid.getAmount()) {
                if (!level.isClientSide) {
                    var remaining = stack.copy();
                    result = GenericItemEmptying.emptyItem(level, remaining, false);
                    fluids.fill(result.getFirst(), FluidAction.EXECUTE);
                    if (!player.isCreative()) {
                        player.setItemInHand(hand, remaining);
                        player.getInventory().placeItemBackInInventory(result.getSecond());
                    }
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        if (GenericItemFilling.canItemBeFilled(level, stack)) for (int tank = 0; tank < fluids.getTanks(); tank++) {
            var fluid = fluids.getFluidInTank(tank);
            if (fluid.isEmpty()) continue;
            int amount = GenericItemFilling.getRequiredAmountForItem(level, stack, fluid.copy());
            if (amount <= 0 || amount > fluid.getAmount()) continue;
            var requested = fluid.copyWithAmount(amount);
            if (!FluidStack.matches(requested, fluids.drain(requested, FluidAction.SIMULATE))) continue;
            if (!level.isClientSide) {
                var remaining = stack.copy();
                var filled = GenericItemFilling.fillItem(level, amount, remaining, fluid.copy());
                fluids.drain(requested, FluidAction.EXECUTE);
                if (!player.isCreative()) {
                    player.setItemInHand(hand, remaining);
                    player.getInventory().placeItemBackInInventory(filled);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    private MachineInteractions() {}
}
