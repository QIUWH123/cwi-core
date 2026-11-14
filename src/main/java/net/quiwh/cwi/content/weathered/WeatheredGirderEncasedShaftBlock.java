/*
 * Adapted from Create Bits 'n' Bobs 2.3.5 (MIT), by Cake, Kipti, NormalGuy,
 * Astral and Spydnel. See META-INF/LICENSE-bits-n-bobs.txt for the license.
 */
package net.quiwh.cwi.content.weathered;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class WeatheredGirderEncasedShaftBlock extends GirderEncasedShaftBlock {
    public WeatheredGirderEncasedShaftBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        Axis shaftAxis = state.getValue(HORIZONTAL_AXIS);
        return WeatheredContent.GIRDER.getDefaultState()
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED))
                .setValue(GirderBlock.X, shaftAxis == Axis.Z)
                .setValue(GirderBlock.Z, shaftAxis == Axis.X)
                .setValue(GirderBlock.AXIS, shaftAxis == Axis.X ? Axis.Z : Axis.X)
                .setValue(GirderBlock.BOTTOM, state.getValue(BOTTOM))
                .setValue(GirderBlock.TOP, state.getValue(TOP));
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
        return ItemRequirement.of(AllBlocks.SHAFT.getDefaultState(), blockEntity)
                .union(ItemRequirement.of(WeatheredContent.GIRDER.getDefaultState(), blockEntity));
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return WeatheredContent.ENCASED_SHAFT.get();
    }
}
