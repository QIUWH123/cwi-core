package net.quiwh.cwi.interaction;

import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** Create owns timing and fluid extraction; simulation is a pure preflight. */
public final class WorldSpouting {
    private record Growth(BlockPos pos, BlockState next) {}

    public static boolean hasRules(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return InteractionRecipes.rules(level, InteractionRecipes.Kind.BLOCK_SPOUTING).stream()
                .anyMatch(holder -> ((InteractionRules.Spouting) holder.value().rule()).input().test(state))
                || InteractionRecipes.rules(level, InteractionRecipes.Kind.BUDDING_CATALYSIS).stream()
                .anyMatch(holder -> ((InteractionRules.Budding) holder.value().rule()).input().test(state));
    }

    /** -1 delegates to the original behaviour; zero is a matching but blocked rule. */
    public static int fill(Level level, BlockPos pos, FluidStack fluid, boolean simulate) {
        BlockState state = level.getBlockState(pos);
        boolean matched = false;
        for (var holder : InteractionRecipes.rules(level, InteractionRecipes.Kind.BLOCK_SPOUTING)) {
            var rule = (InteractionRules.Spouting) holder.value().rule();
            if (!rule.input().test(state) || !rule.fluid().ingredient().test(fluid)) continue;
            matched = true;
            if (fluid.getAmount() < rule.fluid().amount()) continue;
            if (!simulate && level instanceof ServerLevel server) {
                Block result = rule.outputs().get(level.random.nextInt(rule.outputs().size()));
                level.setBlockAndUpdate(pos, result.withPropertiesOf(state));
                AllSoundEvents.SPOUTING.playOnServer(level, pos);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, result.defaultBlockState()),
                        pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 8, .25, .25, .25, .3);
            }
            return rule.fluid().amount();
        }
        for (var holder : InteractionRecipes.rules(level, InteractionRecipes.Kind.BUDDING_CATALYSIS)) {
            var rule = (InteractionRules.Budding) holder.value().rule();
            if (!rule.input().test(state) || !rule.fluid().ingredient().test(fluid)) continue;
            matched = true;
            if (fluid.getAmount() < rule.fluid().amount()) continue;
            List<Growth> available = growth(level, pos, rule.stages());
            if (available.isEmpty()) return 0;
            if (!simulate && level instanceof ServerLevel) {
                AllSoundEvents.SPOUTING.playOnServer(level, pos);
                for (int attempt = 0; attempt < rule.attempts() && !available.isEmpty(); attempt++) {
                    Growth selected = available.get(level.random.nextInt(available.size()));
                    if (level.random.nextDouble() < rule.chance()) level.setBlockAndUpdate(selected.pos(), selected.next());
                    available = growth(level, pos, rule.stages());
                }
            }
            return rule.fluid().amount();
        }
        return matched ? 0 : -1;
    }

    private static List<Growth> growth(Level level, BlockPos pos, List<Block> stages) {
        var available = new ArrayList<Growth>();
        for (Direction direction : Direction.values()) {
            BlockPos target = pos.relative(direction);
            if (!level.hasChunkAt(target)) continue;
            var old = level.getBlockState(target);
            int stage = stages.indexOf(old.getBlock());
            if (stage == stages.size() - 1) continue;
            boolean water = old.getFluidState().is(Fluids.WATER) && old.getFluidState().isSource();
            if (stage < 0 && !old.isAir() && !water) continue;
            // A neighboring crystal attached to another block must never be hijacked.
            if (stage >= 0 && old.hasProperty(BlockStateProperties.FACING)
                    && old.getValue(BlockStateProperties.FACING) != direction) continue;
            BlockState next = stages.get(stage + 1).defaultBlockState();
            if (next.hasProperty(BlockStateProperties.FACING)) next = next.setValue(BlockStateProperties.FACING, direction);
            if (next.hasProperty(BlockStateProperties.WATERLOGGED))
                next = next.setValue(BlockStateProperties.WATERLOGGED, water || old.hasProperty(BlockStateProperties.WATERLOGGED)
                        && old.getValue(BlockStateProperties.WATERLOGGED));
            if (next.canSurvive(level, target)) available.add(new Growth(target, next));
        }
        return available;
    }
    private WorldSpouting() {}
}
