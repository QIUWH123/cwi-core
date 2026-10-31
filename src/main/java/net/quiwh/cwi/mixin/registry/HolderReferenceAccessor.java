package net.quiwh.cwi.mixin.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor<T> {
    @Invoker("bindKey") void cwi$bindKey(ResourceKey<T> key);
    @Invoker("bindValue") void cwi$bindValue(T value);
}
