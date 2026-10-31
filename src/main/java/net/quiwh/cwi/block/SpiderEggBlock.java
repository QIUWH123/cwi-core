package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Preserves turtle-egg states and lifecycle, adding one spider when newly placed. */
public final class SpiderEggBlock extends TurtleEggBlock {
    public static final MapCodec<TurtleEggBlock> CODEC = simpleCodec(SpiderEggBlock::new);

    public SpiderEggBlock(Properties properties) { super(properties); }

    @Override public MapCodec<TurtleEggBlock> codec() { return CODEC; }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                                     boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            Spider spider = EntityType.SPIDER.create(level);
            if (spider != null) {
                spider.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                level.addFreshEntity(spider);
            }
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }
}
