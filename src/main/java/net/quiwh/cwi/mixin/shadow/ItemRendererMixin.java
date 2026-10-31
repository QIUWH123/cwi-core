package net.quiwh.cwi.mixin.shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.shadow.ShadowDropRules;
import org.spongepowered.asm.mixin.Mixin;

/** Supply the item to Shadow Drop's stackless color lookup without changing its renderer. */
@Mixin(value = ItemRenderer.class, priority = 2000)
public abstract class ItemRendererMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V")
    private void cwi$shadowStyle(ItemStack stack, ItemDisplayContext display, boolean leftHand,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original) {
        if (display != ItemDisplayContext.GUI) {
            original.call(stack, display, leftHand, pose, buffers, light, overlay, model);
            return;
        }
        var previous = ShadowDropRules.enterRender(stack);
        try {
            original.call(stack, display, leftHand, pose, buffers, light, overlay, model);
        } finally {
            ShadowDropRules.leaveRender(previous);
        }
    }
}
