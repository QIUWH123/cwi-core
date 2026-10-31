package net.quiwh.cwi.mixin.conversion;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlazeBurnerBlockItem.class)
public abstract class BlazeBurnerBlockItemMixin extends BlockItem {
    protected BlazeBurnerBlockItemMixin(Block block, Properties properties) { super(block, properties); }
    @Shadow public abstract boolean hasCapturedBlaze();

    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
    private void cwi$disableMobCapture(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void cwi$placeWithoutSpawnerCapture(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!hasCapturedBlaze()) cir.setReturnValue(super.useOn(context));
    }
}
