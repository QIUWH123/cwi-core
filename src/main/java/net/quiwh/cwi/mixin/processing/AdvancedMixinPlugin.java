package net.quiwh.cwi.mixin.processing;
import java.util.*;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class AdvancedMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String,String> MODS=Map.of("com.mrh0.createaddition.","createaddition","com.negodya1.vintageimprovements.","vintageimprovements",
            "io.github.hadron13.rubberworks.","rubberworks","com.drmangotea.tfmg.","tfmg","fr.lucreeper74.createmetallurgy.","createmetallurgy","com.jesz.createdieselgenerators.","createdieselgenerators");
    public void onLoad(String name){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        if(mixin.endsWith("JeiMixin")&&(FMLLoader.getLoadingModList()==null||FMLLoader.getLoadingModList().getModFileById("jei")==null))return false;
        for(var rule:MODS.entrySet())if(target.startsWith(rule.getKey()))return FMLLoader.getLoadingModList()!=null&&FMLLoader.getLoadingModList().getModFileById(rule.getValue())!=null;
        return target.startsWith("com.simibubi.create.")||target.startsWith("net.minecraft.");
    }
    public void acceptTargets(Set<String> mine,Set<String> other){}
    public List<String> getMixins(){return null;}
    public void preApply(String name,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String name,ClassNode node,String mixin,IMixinInfo info){}
}
