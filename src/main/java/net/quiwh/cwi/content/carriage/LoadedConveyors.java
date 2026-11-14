package net.quiwh.cwi.content.carriage;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded native controllers available for assembly discovery. */
public final class LoadedConveyors {
    // Neither the values nor their position keys retain the weakly keyed world.
    private static final Map<Level, Map<BlockPos, WeakReference<ChainConveyorBlockEntity>>> LEVELS = new WeakHashMap<>();
    private LoadedConveyors() { }

    public static synchronized void add(ChainConveyorBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide || entity.isRemoved()) return;
        LEVELS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(entity.getBlockPos().immutable(), new WeakReference<>(entity));
    }

    public static synchronized void remove(ChainConveyorBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide) return;
        var entries = LEVELS.get(level);
        if (entries == null) return;
        var reference = entries.get(entity.getBlockPos());
        if (reference != null && reference.get() == entity) entries.remove(entity.getBlockPos());
        if (entries.isEmpty()) LEVELS.remove(level);
    }

    /** A snapshot is requested only during assembly, never by an attached carriage tick. */
    static synchronized List<ChainConveyorBlockEntity> in(Level level) {
        if (level.isClientSide) return List.of();
        var entries = LEVELS.get(level);
        if (entries == null) return List.of();
        List<ChainConveyorBlockEntity> result = new ArrayList<>();
        entries.entrySet().removeIf(entry -> {
            var entity = entry.getValue().get();
            if (entity == null || entity.isRemoved() || entity.getLevel() != level
                    || !level.hasChunkAt(entry.getKey()) || level.getBlockEntity(entry.getKey()) != entity) return true;
            result.add(entity);
            return false;
        });
        if (entries.isEmpty()) LEVELS.remove(level);
        return result;
    }
}
