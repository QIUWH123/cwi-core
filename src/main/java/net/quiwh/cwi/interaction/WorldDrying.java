package net.quiwh.cwi.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldDrying {
    public static boolean randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (var holder : InteractionRecipes.rules(level, InteractionRecipes.Kind.DRYING)) {
            var rule = (InteractionRules.Drying) holder.value().rule();
            if (!rule.input().test(state)) continue;
            if (random.nextDouble() < rule.chance())
                return level.setBlockAndUpdate(pos, rule.output().withPropertiesOf(state));
            return false;
        }
        return false;
    }
    private WorldDrying() {}
}
