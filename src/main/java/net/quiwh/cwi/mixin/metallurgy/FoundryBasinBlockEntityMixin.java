package net.quiwh.cwi.mixin.metallurgy;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "fr.lucreeper74.createmetallurgy.content.blocks.foundry_basin.FoundryBasinBlockEntity", remap = false)
abstract class FoundryBasinBlockEntityMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/basin/BasinInventory;<init>(ILcom/simibubi/create/content/processing/basin/BasinBlockEntity;)V",
            ordinal = 0), index = 0)
    private int cwi$fourInputSlots(int original) { return 4; }

    @ModifyArg(method = "read", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V"), index = 0)
    private CompoundTag cwi$upgradeLegacyInputs(CompoundTag original) {
        var copy = original.copy();
        var inputs = copy.getCompound("InputItems");
        inputs.putInt("Size", 4);
        copy.put("InputItems", inputs);
        return copy;
    }
}
