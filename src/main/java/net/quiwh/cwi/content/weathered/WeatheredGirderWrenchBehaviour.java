/*
 * Adapted from Create Bits 'n' Bobs 2.3.5 (MIT), by Cake, Kipti, NormalGuy,
 * Astral and Spydnel. See META-INF/LICENSE-bits-n-bobs.txt for the license.
 */
package net.quiwh.cwi.content.weathered;

import com.simibubi.create.content.decoration.girder.GirderBlock;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.placement.IPlacementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Flange edits are resolved from the same local selection on the client and server. */
public final class WeatheredGirderWrenchBehaviour {
    public enum Action { SINGLE, PAIR }

    @Nullable
    static Pair<Direction, Action> getDirectionAndAction(BlockHitResult hit, BlockGetter level, BlockPos pos) {
        List<Pair<Direction, Action>> valid = getValidDirections(level, pos);
        if (valid.isEmpty()) return null;
        List<Direction> ordered = IPlacementHelper.orderedByDistance(pos, hit.getLocation(),
                valid.stream().map(Pair::getFirst).toList());
        if (ordered.isEmpty()) return null;
        Direction direction = ordered.getFirst();
        return valid.stream().filter(pair -> pair.getFirst() == direction).findFirst().orElse(null);
    }

    public static List<Pair<Direction, Action>> getValidDirections(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!WeatheredContent.GIRDER.has(state)
                || state.getValue(GirderBlock.X) == state.getValue(GirderBlock.Z)) return List.of();
        List<Pair<Direction, Action>> valid = new ArrayList<>(2);
        for (Direction direction : Iterate.directions) {
            if (direction.getAxis() != Axis.Y) continue;
            BlockState other = level.getBlockState(pos.relative(direction));
            if (!WeatheredContent.GIRDER.has(other)) {
                valid.add(Pair.of(direction, Action.SINGLE));
            } else if (other.getValue(GirderBlock.X) != other.getValue(GirderBlock.Z)) {
                valid.add(Pair.of(direction, Action.PAIR));
            }
        }
        return valid;
    }

    public static boolean handleClick(Level level, BlockPos pos, BlockState state, BlockHitResult hit) {
        Pair<Direction, Action> selected = getDirectionAndAction(hit, level, pos);
        if (selected == null) return false;
        if (level.isClientSide) return true;
        if (!state.getValue(GirderBlock.X) && !state.getValue(GirderBlock.Z)) return false;
        Direction direction = selected.getFirst();
        BlockPos otherPos = pos.relative(direction);
        BlockState other = level.getBlockState(otherPos);
        var flange = direction == Direction.UP ? GirderBlock.TOP : GirderBlock.BOTTOM;
        var otherFlange = direction == Direction.UP ? GirderBlock.BOTTOM : GirderBlock.TOP;
        level.setBlock(pos, postProcess(state.cycle(flange)), 18);
        if (selected.getSecond() == Action.PAIR && WeatheredContent.GIRDER.has(other)) {
            level.setBlock(otherPos, postProcess(other.cycle(otherFlange)), 18);
        }
        return true;
    }

    private static BlockState postProcess(BlockState state) {
        if ((state.getValue(GirderBlock.TOP) && state.getValue(GirderBlock.BOTTOM))
                || state.getValue(GirderBlock.AXIS) != Axis.Y) return state;
        return state.setValue(GirderBlock.AXIS, state.getValue(GirderBlock.X) ? Axis.X : Axis.Z);
    }

    private WeatheredGirderWrenchBehaviour() {}
}
