package net.quiwh.cwi.mixin.pollution;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.drain.ItemDrainRenderer;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.quiwh.cwi.pollution.GasProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=ItemDrainRenderer.class,remap=false)
abstract class ItemDrainGasRenderingMixin {
    @WrapOperation(method="renderFluid",at=@At(value="INVOKE",target="Lnet/createmod/catnip/render/FluidRenderHelper;renderFluidBox(Ljava/lang/Object;FFFFFFLnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IZZ)V"))
    private void cwi$noGasLiquid(FluidRenderHelper<?> renderer,Object fluid,float x1,float y1,float z1,float x2,float y2,float z2,
                                MultiBufferSource buffers,PoseStack pose,int light,boolean top,boolean bottom,Operation<Void> original) {
        if(fluid instanceof FluidStack stack&&!stack.isEmpty()&&GasProfiles.isGas(stack.getFluid()))return;
        original.call(renderer,fluid,x1,y1,z1,x2,y2,z2,buffers,pose,light,top,bottom);
    }
}
