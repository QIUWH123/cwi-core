package net.quiwh.cwi.mixin.rubberworks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.hadron13.rubberworks.blocks.compressor.CompressingRecipe", remap = false)
abstract class CompressingRecipeMixin extends com.simibubi.create.content.processing.recipe.StandardProcessingRecipe<net.minecraft.world.item.crafting.SingleRecipeInput> {
    protected CompressingRecipeMixin(com.simibubi.create.foundation.recipe.IRecipeTypeInfo type,com.simibubi.create.content.processing.recipe.ProcessingRecipeParams params){super(type,params);}
    @Override protected int getMaxFluidOutputCount(){return 6;}

    @Inject(method = "getMaxOutputCount", at = @At("HEAD"), cancellable = true)
    private void cwi$outputLimit(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(6); }
}
