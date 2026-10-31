package net.quiwh.cwi.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public final class PetrifiedLogBlock extends RotatedPillarBlock {
    private final Supplier<Block> stripped;
    public PetrifiedLogBlock(Properties properties, Supplier<Block> stripped) { super(properties); this.stripped = stripped; }
    @Override public MapCodec<? extends RotatedPillarBlock> codec() { return simpleCodec(p -> new PetrifiedLogBlock(p, stripped)); }
    @Override public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        return ability == ItemAbilities.AXE_STRIP ? stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS))
                : super.getToolModifiedState(state, context, ability, simulate);
    }
}
