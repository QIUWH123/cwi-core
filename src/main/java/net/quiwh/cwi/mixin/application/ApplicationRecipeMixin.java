package net.quiwh.cwi.mixin.application;

import com.simibubi.create.content.kinetics.deployer.*;
import net.quiwh.cwi.interaction.application.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ItemApplicationRecipe.class, remap = false)
abstract class ApplicationRecipeMixin implements ApplicationExtension {
    @Unique private ApplicationOptions cwi$options;
    public ApplicationOptions cwi$interaction() { return cwi$options; }
    public void cwi$interaction(ApplicationOptions options) { cwi$options = options; }
    @Inject(method = "getMaxOutputCount", at = @At("HEAD"), cancellable = true)
    private void cwi$moreDrops(CallbackInfoReturnable<Integer> cir) {
        if ((Object)this instanceof ManualApplicationRecipe) cir.setReturnValue(128);
    }
    @Inject(method = "matches(Lnet/neoforged/neoforge/items/wrapper/RecipeWrapper;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
    private void cwi$manualOnly(net.neoforged.neoforge.items.wrapper.RecipeWrapper inventory, net.minecraft.world.level.Level level, CallbackInfoReturnable<Boolean> cir) {
        if (cwi$options != null) cir.setReturnValue(false);
    }
}
