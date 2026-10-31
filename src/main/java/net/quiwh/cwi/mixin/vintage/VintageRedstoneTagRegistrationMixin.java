package net.quiwh.cwi.mixin.vintage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.createmod.ponder.api.registration.MultiTagBuilder;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.infrastructure.ponder.VintagePonderTag", remap = false)
abstract class VintageRedstoneTagRegistrationMixin {
    @WrapOperation(method = "register", at = @At(value = "INVOKE",
            target = "Lnet/createmod/ponder/api/registration/MultiTagBuilder$Tag;add(Ljava/lang/Object;)Lnet/createmod/ponder/api/registration/MultiTagBuilder$Tag;"))
    private static MultiTagBuilder.Tag<?> cwi$skipRemovedModule(MultiTagBuilder.Tag<?> builder, Object component,
            Operation<MultiTagBuilder.Tag<?>> original) {
        if (component instanceof DeferredHolder<?, ?> holder && holder.getId().toString().equals("vintageimprovements:redstone_module")) return builder;
        return original.call(builder, component);
    }
}
