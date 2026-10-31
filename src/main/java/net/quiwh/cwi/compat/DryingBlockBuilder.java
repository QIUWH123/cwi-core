package net.quiwh.cwi.compat;

import dev.latvian.mods.kubejs.block.custom.CardinalBlockBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.quiwh.cwi.interaction.WorldDrying;

/** Native drying with the existing KubeJS cardinal model, shape and item generation. */
public final class DryingBlockBuilder extends CardinalBlockBuilder {
    public DryingBlockBuilder(ResourceLocation id) { super(id); }
    @Override public BlockBehaviour.Properties createProperties() { return super.createProperties().randomTicks().noOcclusion(); }
    @Override public Block createObject() {
        if (blockEntityInfo != null) throw new IllegalArgumentException("cwi:drying does not support block entities");
        return new DryingBlock(this);
    }
    public static final class DryingBlock extends CardinalKubeBlock {
        DryingBlock(DryingBlockBuilder builder) { super(builder); }
        @Override public boolean isRandomlyTicking(BlockState state) { return true; }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            WorldDrying.randomTick(state, level, pos, random);
        }
    }
}
