package net.quiwh.cwi.mixin.pollution;

import java.util.*;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class GasMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p){} public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        if(mixin.contains(".diesel."))return FMLLoader.getLoadingModList().getModFileById("createdieselgenerators")!=null;
        return !mixin.contains(".kubejs.")||FMLLoader.getLoadingModList().getModFileById("kubejs")!=null;
    }
    public void acceptTargets(Set<String>a,Set<String>b){} public List<String> getMixins(){return null;}
    public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
