package net.quiwh.cwi.compat;

/** Recipe metadata only; does not change assembly rolls or processing. */
public interface AssemblyOutputVisibility {
    boolean cwi$showOutputs();
    void cwi$showOutputs(boolean show);
}
