package net.quiwh.cwi.pollution;

import java.util.List;
import net.neoforged.neoforge.fluids.FluidStack;

/** Existing native overflow remains saved while an atmospheric outlet is blocked. */
public interface GasBasinStorage {
    List<FluidStack> cwi$gasOverflow();
}
