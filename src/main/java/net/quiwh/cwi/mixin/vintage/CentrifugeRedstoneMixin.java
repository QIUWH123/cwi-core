package net.quiwh.cwi.mixin.vintage;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Comparator capability is intrinsic; the old installation flag is presentation/save data only. */
@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.centrifuge.CentrifugeBlockEntity", remap = false)
abstract class CentrifugeRedstoneMixin {
    @Shadow private boolean redstoneApp;
    @Shadow private boolean contentsChanged;

    @Inject(method = "getRedstoneApp", at = @At("HEAD"), cancellable = true)
    private void cwi$builtInComparator(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(true); }

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
            target = "Lcom/negodya1/vintageimprovements/content/kinetics/centrifuge/CentrifugeBlockEntity;redstoneApp:Z"))
    private boolean cwi$notifyStructuralComparators(boolean installed) { return true; }

    @ModifyExpressionValue(method = {"write", "destroy", "addToGoggleTooltip"}, at = @At(value = "FIELD",
            target = "Lcom/negodya1/vintageimprovements/content/kinetics/centrifuge/CentrifugeBlockEntity;redstoneApp:Z"))
    private boolean cwi$noInstalledModule(boolean installed) { return false; }

    @Inject(method = "read", at = @At("RETURN"))
    private void cwi$discardLegacyAttachment(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        redstoneApp = false;
        contentsChanged = true;
    }

    @Inject(method = "addRedstoneApp", at = @At("HEAD"), cancellable = true)
    private void cwi$noModuleInstallation(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(false); }
}
