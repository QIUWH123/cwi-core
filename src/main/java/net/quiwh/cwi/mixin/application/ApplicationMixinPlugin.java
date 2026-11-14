package net.quiwh.cwi.mixin.application;
import java.util.*;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class ApplicationMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String name) {
        var mods=FMLLoader.getLoadingModList();
        return mods!=null && (!name.endsWith("ApplicationCategoryMixin") || mods.getModFileById("jei")!=null);
    }
    public void acceptTargets(Set<String> a,Set<String> b) {}
    public List<String> getMixins() { return null; }
    public void preApply(String a,ClassNode b,String c,IMixinInfo d) {}
    public void postApply(String a,ClassNode b,String c,IMixinInfo d) {}
}
