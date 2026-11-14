/*
 * Adapted from Create Bits 'n' Bobs 2.3.5 (MIT), by Cake, Kipti, NormalGuy,
 * Astral and Spydnel. See META-INF/LICENSE-bits-n-bobs.txt for the license.
 */
package net.quiwh.cwi.content.weathered;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/** Native Create girder geometry and connections, with the weathered shaft variant. */
public class WeatheredGirderBlock extends GirderBlock {
    private static final int PLACEMENT_HELPER = PlacementHelpers.register(new WeatheredGirderPlacementHelper());

    public WeatheredGirderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        // Create 6.0.10 reads the old Z value twice; preserve both beam axes on quarter turns.
        BlockState rotated = state.setValue(AXIS,
                rotation.rotate(Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE)).getAxis());
        return rotation.rotate(Direction.EAST).getAxis() == Axis.X ? rotated
                : rotated.setValue(X, state.getValue(Z)).setValue(Z, state.getValue(X));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (player == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (AllBlocks.SHAFT.isIn(stack)) {
            Axis shaftAxis = !state.getValue(X) && hit.getDirection().getAxis() != Axis.Z ? Axis.X : Axis.Z;
            KineticBlockEntity.switchToBlockState(level, pos, WeatheredContent.GIRDER_SHAFT.getDefaultState()
                    .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED))
                    .setValue(TOP, state.getValue(TOP))
                    .setValue(BOTTOM, state.getValue(BOTTOM))
                    .setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, shaftAxis));
            level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, 0.5F, 1.25F);
            if (!level.isClientSide && !player.isCreative()) {
                stack.shrink(1);
                if (stack.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (AllItems.WRENCH.isIn(stack) && !player.isShiftKeyDown()) {
            return WeatheredGirderWrenchBehaviour.handleClick(level, pos, state, hit)
                    ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
        }
        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER);
        return helper.matchesItem(stack)
                ? helper.getOffset(player, level, state, pos, hit)
                        .placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
