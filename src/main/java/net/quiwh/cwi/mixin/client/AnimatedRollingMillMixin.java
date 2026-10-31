package net.quiwh.cwi.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.mrh0.createaddition.compat.jei.AnimatedRollingMill", remap = false)
abstract class AnimatedRollingMillMixin {
    // Match Create's centered block rotation at the native22px scale, after its GUI Y flip.
    @Inject(method = "draw", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V"))
    private void cwi$centerMachineRotation(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        graphics.pose().translate(11, -11, 11);
    }

    @Inject(method = "draw", at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V"))
    private void cwi$restoreMachineOrigin(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        graphics.pose().translate(-11, 11, -11);
    }
}
