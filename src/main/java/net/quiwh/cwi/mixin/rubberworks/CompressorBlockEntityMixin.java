package net.quiwh.cwi.mixin.rubberworks;

import com.simibubi.create.foundation.item.SmartInventory;
import io.github.hadron13.rubberworks.blocks.compressor.CompressingRecipe;
import net.minecraft.nbt.CompoundTag;
import net.quiwh.cwi.compat.ProcessingOutputCapacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.hadron13.rubberworks.blocks.compressor.CompressorBlockEntity", remap = false)
abstract class CompressorBlockEntityMixin {
    @Shadow public SmartInventory output;
    @Shadow public CompressingRecipe recipe;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/item/SmartInventory;<init>(ILcom/simibubi/create/foundation/blockEntity/SyncedBlockEntity;)V"), index = 0)
    private int cwi$outputSlots(int original) { return 6; }

    @ModifyArg(method = "read", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/item/SmartInventory;deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V"), index = 1)
    private CompoundTag cwi$upgradeOutputInventory(CompoundTag original) {
        var copy = original.copy();
        copy.putInt("Size", 6);
        return copy;
    }

    @Inject(method = "process", at = @At(value = "INVOKE",
            target = "Lio/github/hadron13/rubberworks/blocks/compressor/CompressingRecipe;getFluidIngredients()Lnet/minecraft/core/NonNullList;"), cancellable = true)
    private void cwi$reserveOutputsBeforeDraining(CallbackInfo ci) {
        if (!ProcessingOutputCapacity.canFitRecipe(output, recipe)) ci.cancel();
    }
}
