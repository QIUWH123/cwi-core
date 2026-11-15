package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;

/**
 * A full block that advances adjacent amethyst-style growth stages.
 *
 * <p>Target IDs resolve at tick time so the quartz variant can grow its
 * script-owned stages while remaining safe when they are not registered.</p>
 */
public final class InfectedBuddingBlock extends Block {
    private static final float GROWTH_CHANCE = 0.2F;

    private final Map<ResourceLocation, ResourceLocation> growthStages;

    public InfectedBuddingBlock(Properties properties, Map<ResourceLocation, ResourceLocation> growthStages) {
        super(properties);
        this.growthStages = Map.copyOf(growthStages);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new InfectedBuddingBlock(properties, growthStages));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (Direction direction : Direction.values()) {
            if (random.nextFloat() >= GROWTH_CHANCE) {
                continue;
            }

            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = level.getBlockState(targetPos);
            ResourceLocation currentId = BuiltInRegistries.BLOCK.getKey(targetState.getBlock());
            ResourceLocation nextId = growthStages.get(currentId);
            if (nextId == null) {
                continue;
            }

            Block nextBlock = BuiltInRegistries.BLOCK.get(nextId);
            if (nextBlock == Blocks.AIR) {
                continue;
            }

            BlockState nextState = nextBlock.defaultBlockState();
            if (nextState.hasProperty(AmethystClusterBlock.FACING)) {
                Direction facing = targetState.hasProperty(AmethystClusterBlock.FACING)
                        ? targetState.getValue(AmethystClusterBlock.FACING)
                        : direction;
                nextState = nextState.setValue(AmethystClusterBlock.FACING, facing);
            }
            if (nextState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                nextState = nextState.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            if (nextState.canSurvive(level, targetPos)) {
                level.setBlock(targetPos, nextState, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // The block itself is a full cube and has no state properties.
    }
}
