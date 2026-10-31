package net.quiwh.cwi.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional targets are selected before their mixins or classes can be loaded. */
public final class RecipeOutputMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var mods = FMLLoader.getLoadingModList();
        if (mods == null) return false;
        String mod = targetClassName.startsWith("com.negodya1.vintageimprovements.") ? "vintageimprovements"
                : targetClassName.startsWith("io.github.hadron13.rubberworks.") ? "rubberworks"
                : targetClassName.startsWith("com.drmangotea.tfmg.") ? "tfmg"
                : targetClassName.startsWith("fr.lucreeper74.createmetallurgy.") ? "createmetallurgy"
                : targetClassName.startsWith("com.mrh0.createaddition.") ? "createaddition" : null;
        return mod != null && mods.getModFileById(mod) != null
                && (!mixinClassName.contains(".client.") || mods.getModFileById("jei") != null);
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) { }
}
