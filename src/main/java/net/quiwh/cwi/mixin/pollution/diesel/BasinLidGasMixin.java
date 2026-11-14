package net.quiwh.cwi.mixin.pollution.diesel;

import com.jesz.createdieselgenerators.content.basin_lid.BasinLidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.quiwh.cwi.pollution.GasContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=BasinLidBlock.class,remap=false)
abstract class BasinLidGasMixin extends Block {
    protected BasinLidGasMixin(Properties properties){super(properties);}
    private static void cwi$release(Level level,BlockPos pos) {
        var owner=level.getBlockEntity(pos.below());if(owner!=null)GasContainers.releaseStored(owner);
    }
    @Inject(method="useItemOn",at=@At("RETURN"))
    private void cwi$opened(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,
                            BlockHitResult hit,CallbackInfoReturnable<ItemInteractionResult> cir){cwi$release(level,pos);}
    @Inject(method="neighborChanged",at=@At("RETURN"))
    private void cwi$powered(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving,CallbackInfo ci){cwi$release(level,pos);}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        super.onRemove(state,level,pos,next,moving);
        if(!state.is(next.getBlock()))cwi$release(level,pos);
    }
}
