/*
 * Adapted from Create Bits 'n' Bobs 2.3.5 (MIT), by Cake, Kipti, NormalGuy,
 * Astral and Spydnel. See META-INF/LICENSE-bits-n-bobs.txt for the license.
 */
package net.quiwh.cwi.content.weathered;

import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class WeatheredGirderCTBehaviour extends ConnectedTextureBehaviour.Base {
    @Override
    @Nullable
    public CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
        return state.hasProperty(GirderBlock.X) && !state.getValue(GirderBlock.X) && !state.getValue(GirderBlock.Z)
                && direction.getAxis() != Axis.Y ? WeatheredGirderClient.POLE_SHIFT : null;
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
                              BlockPos otherPos, Direction face) {
        return (other.getBlock() == state.getBlock() || other.getBlock() instanceof GirderBlock)
                && !other.getValue(GirderBlock.X) && !other.getValue(GirderBlock.Z);
    }
}
