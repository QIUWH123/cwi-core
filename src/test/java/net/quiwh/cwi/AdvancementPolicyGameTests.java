package net.quiwh.cwi;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CwiCore.ID)
@PrefixGameTestTemplate(false)
public final class AdvancementPolicyGameTests {
    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void advancementsAreAbsentWithoutRemovingRecipes(GameTestHelper helper) {
        verifyLoaded(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = CwiCore.ID)
    public static void onlyAdvancementLoadingSkipsResourceScanning(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var scans = new AtomicInteger();
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listResources")) scans.incrementAndGet();
                    try {
                        return method.invoke(server.getResourceManager(), args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        var prepare = SimpleJsonResourceReloadListener.class.getDeclaredMethod("prepare",
                ResourceManager.class, ProfilerFiller.class);
        prepare.setAccessible(true);
        var data = (Map<?, ?>) prepare.invoke(server.getAdvancements(), resources, InactiveProfiler.INSTANCE);
        helper.assertTrue(data.isEmpty() && scans.get() == 0,
                "Advancement reload skips resource discovery and JSON parsing");

        var definitions = new JsonProbe().read(resources);
        helper.assertTrue(!definitions.isEmpty() && scans.get() > 0,
                "Other JSON listeners and direct advancement-file readers still see their resources");
        helper.succeed();
    }

    static void verifyLoaded(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getAdvancements().getAllAdvancements().isEmpty(),
                "No vanilla, modded or recipe-unlock advancements load");
        helper.assertTrue(!server.getAdvancements().tree().roots().iterator().hasNext(),
                "The advancement UI has no roots");
        helper.assertTrue(server.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("crafting_table")).isPresent(),
                "Removing recipe-unlock advancements does not remove recipe definitions");
    }

    private static final class JsonProbe extends SimpleJsonResourceReloadListener {
        JsonProbe() {
            super(new Gson(), "advancement");
        }

        Map<ResourceLocation, JsonElement> read(ResourceManager resources) {
            return prepare(resources, InactiveProfiler.INSTANCE);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager resources, ProfilerFiller profiler) {}
    }
}
