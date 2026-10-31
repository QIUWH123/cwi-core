package net.quiwh.cwi.machine;

import com.mojang.serialization.MapCodec;

public final class BlastFurnaceBlock extends MachineBlock {
    public static final MapCodec<BlastFurnaceBlock> CODEC = simpleCodec(BlastFurnaceBlock::new);
    public BlastFurnaceBlock(Properties properties) { super(properties, Kind.BLAST_FURNACE, Shaft.NONE); }
    @Override public MapCodec<? extends MachineBlock> codec() { return CODEC; }
}
