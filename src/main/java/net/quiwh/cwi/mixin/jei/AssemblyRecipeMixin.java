package net.quiwh.cwi.mixin.jei;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.quiwh.cwi.compat.AssemblyOutputVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = SequencedAssemblyRecipe.class, remap = false)
abstract class AssemblyRecipeMixin implements AssemblyOutputVisibility {
    @Unique private boolean cwi$showOutputs;
    public boolean cwi$showOutputs() { return cwi$showOutputs; }
    public void cwi$showOutputs(boolean show) { cwi$showOutputs = show; }
}
