package net.quiwh.cwi.mixin.pollution;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.material.Fluid;
import net.quiwh.cwi.pollution.GasProfiles;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
abstract class GasBucketMixin {
    @Shadow @Final public Fluid content;
    @Inject(method="emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),cancellable=true)
    private void cwi$noPlacement(Player p,Level level,BlockPos pos,BlockHitResult hit,ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        if(GasProfiles.fromFluid(content)!=null)cir.setReturnValue(false);
    }
}
