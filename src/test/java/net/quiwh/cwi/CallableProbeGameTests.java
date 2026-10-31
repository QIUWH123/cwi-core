package net.quiwh.cwi;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Keep the discoverable test class free of optional types, including verifier locals. */
@GameTestHolder("cwi") @PrefixGameTestTemplate(false)
public final class CallableProbeGameTests {
    @GameTest(template = "empty", templateNamespace = "cwi")
    public static void probeJsDumpsActualCallableAndNativeDeclarations(GameTestHelper helper) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("probejs")) { helper.succeed(); return; }
        try { CallableProbeDumpContract.verify(helper); }
        catch (Exception | AssertionError failure) { failure.printStackTrace(); throw failure; }
    }
}
