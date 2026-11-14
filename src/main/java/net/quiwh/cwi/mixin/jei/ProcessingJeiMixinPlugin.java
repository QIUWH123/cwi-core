package net.quiwh.cwi.mixin.jei;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Client classes stay unloaded without JEI; existing JET owns its displays when installed. */
public final class ProcessingJeiMixinPlugin implements IMixinConfigPlugin {
    public static boolean enabled(String mixin, Predicate<String> loaded) {
        if (!mixin.startsWith("net.quiwh.cwi.mixin.jei.")) throw new IllegalArgumentException(mixin);
        if (mixin.endsWith("AssemblyRecipeMixin") || mixin.endsWith("AssemblySerializerMixin")) return loaded.test("create");
        return loaded.test("create") && loaded.test("jei") && !loaded.test("createjustenoughtime")
                && (!mixin.endsWith("VintageTimingMixin") || loaded.test("vintageimprovements"));
    }
    public void onLoad(String name) { }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        var mods = FMLLoader.getLoadingModList();
        return mods != null && enabled(mixin, id -> mods.getModFileById(id) != null);
    }
    public void acceptTargets(Set<String> mine, Set<String> others) { }
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
