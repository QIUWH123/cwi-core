package net.quiwh.cwi;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A green result must include the requested integration matrix, not merely the tests that happened to load. */
@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class VerificationProfileGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void requestedVerificationProfileIsPresent(GameTestHelper helper) {
        for (String id : System.getProperty("cwi.verification.mods", "").split(","))
            if (!id.isBlank()) helper.assertTrue(ModList.get().isLoaded(id), "Required verification mod is missing: " + id);
        var names = GameTestRegistry.getAllTestFunctions().stream().map(test -> test.testName().toLowerCase(java.util.Locale.ROOT)).toList();
        for (String method : System.getProperty("cwi.verification.tests", "examplesloadandreload,retainedorerecipesload").split(","))
            helper.assertTrue(names.stream().anyMatch(name -> name.equals(method) || name.endsWith("." + method)),
                    "Required verification test was not registered: " + method);
        helper.succeed();
    }
}
