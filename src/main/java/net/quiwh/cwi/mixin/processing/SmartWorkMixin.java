package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.quiwh.cwi.compat.processing.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SmartBlockEntity.class,remap=false)
abstract class SmartWorkMixin implements NativeWorkOwner {
    @Unique private NativeWork cwi$work;
    public NativeWork cwi$work(){if(cwi$work==null)cwi$work=new NativeWork();return cwi$work;}
    @Inject(method="write",at=@At("TAIL"))
    private void cwi$save(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){if(cwi$work!=null&&cwi$work.active())tag.put("CwiProcessing",cwi$work.save());}
    @Inject(method="read",at=@At("TAIL"))
    private void cwi$load(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci){if(!packet&&(cwi$work!=null||tag.contains("CwiProcessing")))cwi$work().load(tag.getCompound("CwiProcessing"));}
}
