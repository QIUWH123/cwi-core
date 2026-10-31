package net.quiwh.cwi.mixin.tfmg;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity", remap = false)
abstract class VatBlockEntityMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/drmangotea/tfmg/content/machinery/vat/base/VatInventory;<init>(ILcom/drmangotea/tfmg/content/machinery/vat/base/VatBlockEntity;)V"),
            index = 0, require = 2, allow = 2)
    private int cwi$itemSlots(int original) { return 8; }

    // The native matcher sizes its scratch inventory and both item scans independently.
    @ModifyConstant(method = "getMatchingRecipe", constant = @Constant(intValue = 4), require = 3, allow = 3)
    private int cwi$matchingSlots(int original) { return 8; }

    @ModifyArg(method = "read", at = @At(value = "INVOKE",
            target = "Lcom/drmangotea/tfmg/content/machinery/vat/base/VatInventory;deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V"),
            index = 1, require = 2, allow = 2)
    private CompoundTag cwi$upgradeItemInventory(CompoundTag original) {
        var copy = original.copy();
        copy.putInt("Size", 8);
        return copy;
    }
}
