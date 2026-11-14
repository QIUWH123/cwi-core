package net.quiwh.cwi;

import net.quiwh.cwi.pollution.GasProfiles;

/** Isolated startup registry, before profiles freeze or any production world loads. */
public final class GasProfileLimitContractTest {
    private static void reject(Runnable operation) {
        try {operation.run();throw new AssertionError("Invalid alias was accepted");}
        catch(IllegalArgumentException expected) {}
    }
    public static void main(String[] args) {
        if (!GasProfiles.values().isEmpty())
            throw new AssertionError("Without startup scripts, Core must not define any gas profiles");
        var first = GasProfiles.configure("fixture:gas_0");
        GasProfiles.alias("fixture:legacy", "fixture:gas_0");
        GasProfiles.alias("fixture:older", "fixture:legacy");
        GasProfiles.alias("fixture:legacy", "fixture:gas_0");
        reject(() -> GasProfiles.configure("fixture:legacy"));
        reject(() -> GasProfiles.alias("fixture:legacy", "fixture:other"));
        reject(() -> GasProfiles.alias("fixture:gas_0", "fixture:other"));
        GasProfiles.alias("fixture:loop_a", "fixture:loop_b");
        reject(() -> GasProfiles.alias("fixture:loop_b", "fixture:loop_a"));
        reject(() -> GasProfiles.alias("fixture:self", "fixture:self"));
        for (int i = 1; i < GasProfiles.MAX_PROFILES - GasProfiles.values().size(); i++)
            GasProfiles.configure("fixture:gas_" + i);
        try {
            GasProfiles.configure("fixture:overflow");
            throw new AssertionError("A new gas must not exceed the profile limit");
        } catch (IllegalArgumentException expected) {}
        if (GasProfiles.configure("fixture:gas_0") != first)
            throw new AssertionError("At capacity, repeated configuration must retain the same builder");
        first.opacity(.3);
        System.out.println("Gas profiles: existing profiles remain configurable at capacity");
    }
}
