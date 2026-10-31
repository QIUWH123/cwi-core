package net.quiwh.cwi.mixin.vintage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.OverlayInstructions;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.quiwh.cwi.client.vintage.ObsoleteModuleControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the native comparator demonstrations, replacing only their obsolete installation steps. */
@Pseudo
@Mixin(targets = {
        "com.negodya1.vintageimprovements.infrastructure.ponder.scenes.CentrifugeScenes",
        "com.negodya1.vintageimprovements.infrastructure.ponder.scenes.CurvingPressScenes"}, remap = false)
abstract class VintageRedstoneScenesMixin {
    @WrapOperation(method = "redstone", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/createmod/ponder/api/element/TextElementBuilder;text(Ljava/lang/String;)Lnet/createmod/ponder/api/element/TextElementBuilder;"))
    private static TextElementBuilder cwi$explainBuiltInComparator(TextElementBuilder builder, String text, Operation<TextElementBuilder> original) {
        // Consume the original numbered text slot so later native translations retain their indices.
        original.call(builder, text);
        var key = ResourceLocation.fromNamespaceAndPath("cwi", "built_in_comparator");
        ((PonderLocalization) PonderIndex.getLangAccess()).registerShared(key,
                "Comparator support is built in. No Redstone Module is needed.");
        return builder.sharedText(key);
    }

    @WrapOperation(method = "redstone", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/createmod/ponder/api/scene/OverlayInstructions;showControls(Lnet/minecraft/world/phys/Vec3;Lnet/createmod/catnip/math/Pointing;I)Lnet/createmod/ponder/api/element/InputElementBuilder;"))
    private static InputElementBuilder cwi$skipModuleInstallation(OverlayInstructions overlay, Vec3 pos, Pointing pointing,
            int duration, Operation<InputElementBuilder> original) {
        return ObsoleteModuleControls.INSTANCE;
    }
}
