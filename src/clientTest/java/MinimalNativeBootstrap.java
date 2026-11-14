import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Test-only empty loader context; does not load mods, fire lifecycle events, or start a game. */
public final class MinimalNativeBootstrap {
    public static void initialize() throws Exception {
        FMLPaths.loadAbsolutePaths(Path.of("build/minimal-native-game").toAbsolutePath());
        var loading = LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        set("loadingModList", loading);
        set("dist", Dist.DEDICATED_SERVER);
    }
    private static void set(String name, Object value) throws Exception {
        Field field = FMLLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
    public static RegisterEvent event(ResourceKey<? extends Registry<?>> key, Registry<?> registry) throws Exception {
        var constructor = RegisterEvent.class.getDeclaredConstructor(ResourceKey.class, Registry.class);
        constructor.setAccessible(true);
        return constructor.newInstance(key, registry);
    }
    public static void main(String[] args) throws Exception {
        initialize();
        Class.forName(args[0]).getMethod("main", String[].class).invoke(null, (Object)new String[0]);
    }
}
