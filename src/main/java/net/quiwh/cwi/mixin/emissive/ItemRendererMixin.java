package net.quiwh.cwi.mixin.emissive;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import net.quiwh.cwi.client.emissive.EmissiveItemRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void cwi$renderEmissiveFaces(ItemRenderer renderer, BakedModel model, ItemStack stack, int light, int overlay,
            PoseStack pose, VertexConsumer consumer, Operation<Void> original,
            ItemStack outerStack, ItemDisplayContext display, boolean leftHand, PoseStack outerPose,
            MultiBufferSource buffers, int outerLight, int outerOverlay, BakedModel outerModel) {
        var parts = EmissiveItemRendering.split(model);
        if (parts.normal() != null) original.call(renderer, parts.normal(), stack, light, overlay, pose, consumer);
        // Acquire the next buffer only after the previous consumer is finished.
        if (parts.emissive() != null) original.call(renderer, parts.emissive(), stack, light, overlay, pose,
                EmissiveItemRendering.buffer(stack, display, pose, buffers));
    }
}
