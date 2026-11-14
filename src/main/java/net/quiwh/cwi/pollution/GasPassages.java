package net.quiwh.cwi.pollution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Conservative block-resolution passages; closed doors seal their whole cell. Never loads chunks. */
public final class GasPassages {
    public static double volume(ServerLevel level, BlockPos pos) {
        if(!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return 0;
        var state=level.getBlockState(pos);
        if(!state.getFluidState().isEmpty() || closedDoor(state)) return 0;
        var shape=state.getCollisionShape(level,pos);
        double solid=0;
        for(var box:shape.toAabbs()) solid+=Math.max(0,Math.min(1,box.maxX)-Math.max(0,box.minX))
                *Math.max(0,Math.min(1,box.maxY)-Math.max(0,box.minY))
                *Math.max(0,Math.min(1,box.maxZ)-Math.max(0,box.minZ));
        return Math.max(0,1-solid);
    }
    private static boolean closedDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN);
    }
    public static boolean connects(ServerLevel level, BlockPos from, Direction direction) {
        var to=from.relative(direction);
        if(volume(level,from)<.01 || volume(level,to)<.01) return false;
        return !Shapes.mergedFaceOccludes(level.getBlockState(from).getCollisionShape(level,from),
                level.getBlockState(to).getCollisionShape(level,to),direction);
    }
    /** Contents start inside the pipe. Its exterior collision shape cannot seal its own vent. */
    public static boolean acceptsOutlet(ServerLevel level, BlockPos output, Direction direction) {
        return volume(level,output)>=.01 && !Shapes.mergedFaceOccludes(Shapes.empty(),
                level.getBlockState(output).getCollisionShape(level,output),direction);
    }
    public static boolean containsAir(ServerLevel level, Vec3 point) {
        var pos=BlockPos.containing(point);
        if(volume(level,pos)<.01) return false;
        for(var box:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs())
            if(box.move(pos).contains(point)) return false;
        return true;
    }
    private GasPassages() {}
}
