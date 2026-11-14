package net.quiwh.cwi.mixin.application;

import com.simibubi.create.content.kinetics.deployer.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.quiwh.cwi.interaction.application.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ManualApplicationRecipe.class, remap = false)
abstract class ManualApplicationMixin {
    @Inject(method = "manualApplicationRecipesApplyInWorld", at = @At("HEAD"), cancellable = true)
    private static void cwi$interaction(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (ManualApplications.apply(event)) ci.cancel();
    }
    @Inject(method = "lambda$manualApplicationRecipesApplyInWorld$0", at = @At("HEAD"), cancellable = true)
    private static void cwi$noFallback(net.minecraft.world.level.block.state.BlockState block, net.minecraft.world.item.ItemStack held,
            RecipeHolder<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (((ApplicationExtension)recipe.value()).cwi$interaction() != null) cir.setReturnValue(false);
    }
    @Inject(method = "asDeploying", at = @At("RETURN"))
    private static void cwi$markGenerated(RecipeHolder<?> recipe, CallbackInfoReturnable<RecipeHolder<DeployerApplicationRecipe>> cir) {
        ((ApplicationExtension)cir.getReturnValue().value()).cwi$interaction(((ApplicationExtension)recipe.value()).cwi$interaction());
    }
}
