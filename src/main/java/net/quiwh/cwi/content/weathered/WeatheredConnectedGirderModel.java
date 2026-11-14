/*
 * Adapted from Create Bits 'n' Bobs 2.3.5 (MIT), by Cake, Kipti, NormalGuy,
 * Astral and Spydnel. See META-INF/LICENSE-bits-n-bobs.txt for the license.
 */
package net.quiwh.cwi.content.weathered;

import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.foundation.block.connected.CTModel;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

@OnlyIn(Dist.CLIENT)
public class WeatheredConnectedGirderModel extends CTModel {
    private static final ModelProperty<Integer> CONNECTIONS = new ModelProperty<>();

    public WeatheredConnectedGirderModel(BakedModel originalModel) {
        super(originalModel, new WeatheredGirderCTBehaviour());
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                               BlockState state, ModelData blockEntityData) {
        super.gatherModelData(builder, world, pos, state, blockEntityData);
        int connections = 0;
        for (Direction direction : Iterate.horizontalDirections) {
            if (GirderBlock.isConnected(world, pos, state, direction)) connections |= 1 << direction.get2DDataValue();
        }
        return builder.with(CONNECTIONS, connections);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data,
                                   RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, random, data, renderType);
        Integer connections = data.get(CONNECTIONS);
        if (side != null || connections == null || connections == 0) return base;
        List<BakedQuad> quads = new ArrayList<>(base);
        for (Direction direction : Iterate.horizontalDirections) {
            if ((connections & (1 << direction.get2DDataValue())) != 0) {
                quads.addAll(WeatheredGirderClient.bracket(direction).get()
                        .getQuads(state, side, random, data, renderType));
            }
        }
        return quads;
    }
}
