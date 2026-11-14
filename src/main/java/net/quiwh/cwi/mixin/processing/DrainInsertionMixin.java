package net.quiwh.cwi.mixin.processing;
import com.simibubi.create.content.fluids.drain.*;
import net.minecraft.world.item.ItemStack;
import net.quiwh.cwi.compat.processing.NativeDrainProducts;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ItemDrainItemHandler.class,remap=false)
abstract class DrainInsertionMixin {
    @Shadow private ItemDrainBlockEntity blockEntity;
    @Inject(method="insertItem",at=@At("HEAD"),cancellable=true)
    private void cwi$wait(int slot,ItemStack stack,boolean simulate,CallbackInfoReturnable<ItemStack> cir){if(((NativeDrainProducts)blockEntity).cwi$pendingProducts())cir.setReturnValue(stack);}
}
